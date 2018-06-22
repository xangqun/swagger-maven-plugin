package com.github.kongchen.swagger.docgen.reader;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.github.kongchen.swagger.docgen.GenerateException;
import com.google.common.collect.Iterables;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.jackson.ModelResolver;
import io.swagger.models.*;
import io.swagger.models.properties.*;

import io.swagger.util.AllowableValues;
import io.swagger.util.AllowableValuesUtils;
import io.swagger.util.PrimitiveType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author chekong on 15/5/19.
 */
public class ModelModifier extends ModelResolver {
    private Map<JavaType, JavaType> modelSubtitutes = new HashMap<JavaType, JavaType>();
    private List<String> apiModelPropertyAccessExclusions = new ArrayList<String>();

    protected final TypeNameResolver _typeNameResolverx = TypeNameResolver.std;

    private static Logger LOGGER = LoggerFactory.getLogger(ModelModifier.class);

    public ModelModifier(ObjectMapper mapper) {
        super(mapper);
    }

    public void addModelSubstitute(String fromClass, String toClass) throws GenerateException {
        JavaType type = null;
        JavaType toType = null;
        try {
            type = _mapper.constructType(Class.forName(fromClass));
        } catch (ClassNotFoundException e) {
            LOGGER.warn(String.format("Problem with loading class: %s. Mapping from: %s to: %s will be ignored.",
                    fromClass, fromClass, toClass));
        }
        try {
            toType = _mapper.constructType(Class.forName(toClass));
        } catch (ClassNotFoundException e) {
            LOGGER.warn(String.format("Problem with loading class: %s. Mapping from: %s to: %s will be ignored.",
                    toClass, fromClass, toClass));
        }
        if(type != null && toType != null) {
            modelSubtitutes.put(type, toType);
        }
    }

    public List<String> getApiModelPropertyAccessExclusions() {
        return apiModelPropertyAccessExclusions;
    }

    public void setApiModelPropertyAccessExclusions(List<String> apiModelPropertyAccessExclusions) {
        this.apiModelPropertyAccessExclusions = apiModelPropertyAccessExclusions;
    }

    @Override
    public Property resolveProperty(Type type, ModelConverterContext context, Annotation[] annotations, Iterator<ModelConverter> chain) {
    	// for method parameter types we get here Type but we need JavaType
    	JavaType javaType = toJavaType(type);
        if (modelSubtitutes.containsKey(javaType)) {
            return super.resolveProperty(modelSubtitutes.get(javaType), context, annotations, chain);
        } else if (chain.hasNext()) {
            return chain.next().resolveProperty(type, context, annotations, chain);
        } else {
            return super.resolveProperty(type, context, annotations, chain);
        }

    }

    @Override
    public Model resolve(Type type, ModelConverterContext context, Iterator<ModelConverter> chain) {
    	// for method parameter types we get here Type but we need JavaType
    	JavaType javaType = toJavaType(type);
        if (modelSubtitutes.containsKey(javaType)) {
            return super.resolve(modelSubtitutes.get(javaType), context, chain);
        } else {
            return super.resolve(type, context, chain);
        }
    }

    @Override
    public Model resolve(JavaType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Model model = resolveprivate(type, context, chain);

        // If there are no @ApiModelPropety exclusions configured, return the untouched model
        if (apiModelPropertyAccessExclusions == null || apiModelPropertyAccessExclusions.isEmpty()) {
            return model;
        }

        Class<?> cls = type.getRawClass();

        for (Method method : cls.getDeclaredMethods()) {
            ApiModelProperty apiModelPropertyAnnotation = AnnotationUtils.findAnnotation(method, ApiModelProperty.class);

            if (apiModelPropertyAnnotation == null) {
                continue;
            }

            String apiModelPropertyAccess = apiModelPropertyAnnotation.access();
            String apiModelPropertyName = apiModelPropertyAnnotation.name();

            // If the @ApiModelProperty is not populated with both #name and #access, skip it
            if (apiModelPropertyAccess.isEmpty() || apiModelPropertyName.isEmpty()) {
                continue;
            }

            // Check to see if the value of @ApiModelProperty#access is one to exclude.
            // If so, remove it from the previously-calculated model.
            if (apiModelPropertyAccessExclusions.contains(apiModelPropertyAccess)) {
                model.getProperties().remove(apiModelPropertyName);
            }
        }

        return model;
    }

    @Override
    protected String _findTypeName(JavaType type, BeanDescription beanDesc) {
        // First, handle container types; they require recursion
        if (type.isArrayType()) {
            return "Array";
        }

        if (type.isMapLikeType()) {
            return "Map";
        }

        if (type.isContainerType()) {
            if (Set.class.isAssignableFrom(type.getRawClass())) {
                return "Set";
            }
            return "List";
        }
        if (beanDesc == null) {
            beanDesc = _mapper.getSerializationConfig().introspectClassAnnotations(type);
        }

        PropertyName rootName = _intr.findRootName(beanDesc.getClassInfo());
        if (rootName != null && rootName.hasSimpleName()) {
            return rootName.getSimpleName();
        }
        return _typeNameResolverx.nameForType(type);
    }

    /**
     * Converts {@link Type} to {@link JavaType}.
     * @param type object to convert
     * @return object converted to {@link JavaType}
     */
    private JavaType toJavaType(Type type) {
		JavaType typeToFind;
    	if (type instanceof JavaType) {
    		typeToFind = (JavaType) type;
    	} else {
    		typeToFind = _mapper.constructType(type);
    	}
		return typeToFind;
	}

    private Model resolveprivate(JavaType type, ModelConverterContext context, Iterator<ModelConverter> next) {
        if (type.isEnumType() || PrimitiveType.fromType(type) != null) {
            // We don't build models for primitive types
            return null;
        }

        final BeanDescription beanDesc = _mapper.getSerializationConfig().introspect(type);
        // Couple of possibilities for defining
        String name = _typeName(type, beanDesc);

        if ("Object".equals(name)) {
            return new ModelImpl();
        }

        /**
         * --Preventing parent/child hierarchy creation loops - Comment 1--
         * Creating a parent model will result in the creation of child models. Creating a child model will result in
         * the creation of a parent model, as per the second If statement following this comment.
         *
         * By checking whether a model has already been resolved (as implemented below), loops of parents creating
         * children and children creating parents can be short-circuited. This works because currently the
         * ModelConverterContextImpl will return null for a class that already been processed, but has not yet been
         * defined. This logic works in conjunction with the early immediate definition of model in the context
         * implemented later in this method (See "Preventing parent/child hierarchy creation loops - Comment 2") to
         * prevent such
         */
        Model resolvedModel = context.resolve(type); //.getRawClass()
        if (resolvedModel != null) {
            if (!(resolvedModel instanceof ModelImpl || resolvedModel instanceof ComposedModel)
                    || (resolvedModel instanceof ModelImpl && ((ModelImpl) resolvedModel).getName().equals(name))) {
                return resolvedModel;
            } else if (resolvedModel instanceof ComposedModel) {
                Model childModel = ((ComposedModel) resolvedModel).getChild();
                if (childModel != null && (!(childModel instanceof ModelImpl)
                        || ((ModelImpl) childModel).getName().equals(name))) {
                    return resolvedModel;
                }
            }
        }

        final ModelImpl model = new ModelImpl().type(ModelImpl.OBJECT).name(name)
                .description(_description(beanDesc.getClassInfo()));

        if (!type.isContainerType()) {
            // define the model here to support self/cyclic referencing of models
            context.defineModel(name, model, type, null);
        }

        if (type.isContainerType()) {
            // We treat collections as primitive types, just need to add models for values (if any)
            context.resolve(type.getContentType());
            return null;
        }
        // if XmlRootElement annotation, construct an Xml object and attach it to the model
        XmlRootElement rootAnnotation = beanDesc.getClassAnnotations().get(XmlRootElement.class);
        if (rootAnnotation != null && !"".equals(rootAnnotation.name()) && !"##default".equals(rootAnnotation.name())) {
            LOGGER.debug("{}", rootAnnotation);
            Xml xml = new Xml().name(rootAnnotation.name());
            if (rootAnnotation.namespace() != null && !"".equals(rootAnnotation.namespace()) && !"##default".equals(rootAnnotation.namespace())) {
                xml.namespace(rootAnnotation.namespace());
            }
            model.xml(xml);
        }
        final XmlAccessorType xmlAccessorTypeAnnotation = beanDesc.getClassAnnotations().get(XmlAccessorType.class);

        // see if @JsonIgnoreProperties exist
        Set<String> propertiesToIgnore = new HashSet<String>();
        JsonIgnoreProperties ignoreProperties = beanDesc.getClassAnnotations().get(JsonIgnoreProperties.class);
        if (ignoreProperties != null) {
            propertiesToIgnore.addAll(Arrays.asList(ignoreProperties.value()));
        }

        final ApiModel apiModel = beanDesc.getClassAnnotations().get(ApiModel.class);
        String disc = (apiModel == null) ? "" : apiModel.discriminator();

        if (apiModel != null && StringUtils.isNotEmpty(apiModel.reference())) {
            model.setReference(apiModel.reference());
        }

        if (disc.isEmpty()) {
            // longer method would involve AnnotationIntrospector.findTypeResolver(...) but:
            JsonTypeInfo typeInfo = beanDesc.getClassAnnotations().get(JsonTypeInfo.class);
            if (typeInfo != null) {
                disc = typeInfo.property();
            }
        }
        if (!disc.isEmpty()) {
            model.setDiscriminator(disc);
        }

        List<Property> props = new ArrayList<Property>();
        for (BeanPropertyDefinition propDef : beanDesc.findProperties()) {
            Property property = null;
            String propName = propDef.getName();
            Annotation[] annotations = null;

            // hack to avoid clobbering properties with get/is names
            // it's ugly but gets around https://github.com/swagger-api/swagger-core/issues/415
            if (propDef.getPrimaryMember() != null) {
                java.lang.reflect.Member member = propDef.getPrimaryMember().getMember();
                if (member != null) {
                    String altName = member.getName();
                    if (altName != null) {
                        final int length = altName.length();
                        for (String prefix : Arrays.asList("get", "is")) {
                            final int offset = prefix.length();
                            if (altName.startsWith(prefix) && length > offset
                                    && !Character.isUpperCase(altName.charAt(offset))) {
                                propName = altName;
                                break;
                            }
                        }
                    }
                }
            }

            PropertyMetadata md = propDef.getMetadata();

            boolean hasSetter = false, hasGetter = false;
            try{
                if (propDef.getSetter() == null) {
                    hasSetter = false;
                } else {
                    hasSetter = true;
                }
            } catch (IllegalArgumentException e){
                //com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder would throw IllegalArgumentException
                // if there are overloaded setters. If we only want to know whether a set method exists, suppress the exception
                // is reasonable.
                // More logs might be added here
                hasSetter = true;
            }
            if (propDef.getGetter() != null) {
                JsonProperty pd = propDef.getGetter().getAnnotation(JsonProperty.class);
                if (pd != null) {
                    hasGetter = true;
                }
            }
            Boolean isReadOnly = null;
            if (!hasSetter & hasGetter) {
                isReadOnly = Boolean.TRUE;
            } else {
                isReadOnly = Boolean.FALSE;
            }

            final AnnotatedMember member = propDef.getPrimaryMember();
            Boolean allowEmptyValue = null;

            if (member != null && !ignore(member, xmlAccessorTypeAnnotation, propName, propertiesToIgnore)) {
                List<Annotation> annotationList = new ArrayList<Annotation>();
                for (Annotation a : member.annotations()) {
                    annotationList.add(a);
                }

                annotations = annotationList.toArray(new Annotation[annotationList.size()]);

                ApiModelProperty mp = member.getAnnotation(ApiModelProperty.class);

                if (mp != null && mp.readOnly()) {
                    isReadOnly = mp.readOnly();
                }

                if (mp != null && mp.allowEmptyValue()) {
                    allowEmptyValue = mp.allowEmptyValue();
                }
                else {
                    allowEmptyValue = null;
                }

                JavaType propType = member.getType(beanDesc.bindingsForBeanType());

                // allow override of name from annotation
                if (mp != null && !mp.name().isEmpty()) {
                    propName = mp.name();
                }

                if (mp != null && !mp.dataType().isEmpty()) {
                    String or = mp.dataType();

                    JavaType innerJavaType = null;
                    LOGGER.debug("overriding datatype from {} to {}", propType, or);

                    if (or.toLowerCase().startsWith("list[")) {
                        String innerType = or.substring(5, or.length() - 1);
                        ArrayProperty p = new ArrayProperty();
                        Property primitiveProperty = PrimitiveType.createProperty(innerType);
                        if (primitiveProperty != null) {
                            p.setItems(primitiveProperty);
                        } else {
                            innerJavaType = getInnerType(innerType);
                            p.setItems(context.resolveProperty(innerJavaType, annotations));
                        }
                        property = p;
                    } else if (or.toLowerCase().startsWith("map[")) {
                        int pos = or.indexOf(",");
                        if (pos > 0) {
                            String innerType = or.substring(pos + 1, or.length() - 1);
                            MapProperty p = new MapProperty();
                            Property primitiveProperty = PrimitiveType.createProperty(innerType);
                            if (primitiveProperty != null) {
                                p.setAdditionalProperties(primitiveProperty);
                            } else {
                                innerJavaType = getInnerType(innerType);
                                p.setAdditionalProperties(context.resolveProperty(innerJavaType, annotations));
                            }
                            property = p;
                        }
                    } else {
                        Property primitiveProperty = PrimitiveType.createProperty(or);
                        if (primitiveProperty != null) {
                            property = primitiveProperty;
                        } else {
                            innerJavaType = getInnerType(or);
                            property = context.resolveProperty(innerJavaType, annotations);
                        }
                    }
                    if (innerJavaType != null) {
                        context.resolve(innerJavaType);
                    }
                }

                // no property from override, construct from propType
                if (property == null) {
                    if (mp != null && StringUtils.isNotEmpty(mp.reference())) {
                        property = new RefProperty(mp.reference());
                    } else if (member.getAnnotation(JsonIdentityInfo.class) != null) {
                        property = GeneratorWrapper.processJsonIdentity(propType, context, _mapper,
                                member.getAnnotation(JsonIdentityInfo.class),
                                member.getAnnotation(JsonIdentityReference.class));
                    }
                    if (property == null) {
                        JsonUnwrapped uw = member.getAnnotation(JsonUnwrapped.class);
                        if (uw != null && uw.enabled()) {
                            handleUnwrapped(props, context.resolve(propType), uw.prefix(), uw.suffix());
                        } else {
                            property = context.resolveProperty(propType, annotations);
                        }
                    }
                }

                if (property != null) {
                    property.setName(propName);

                    if (mp != null && !mp.access().isEmpty()) {
                        property.setAccess(mp.access());
                    }

                    Boolean required = md.getRequired();
                    if (required != null) {
                        property.setRequired(required);
                    }

                    String description = _intr.findPropertyDescription(member);
                    if (description != null && !"".equals(description)) {
                        property.setDescription(description);
                    }

                    Integer index = _intr.findPropertyIndex(member);
                    if (index != null) {
                        property.setPosition(index);
                    }
                    property.setDefault(_findDefaultValue(member));
                    property.setExample(_findExampleValue(member));
                    property.setReadOnly(_findReadOnly(member));
                    if(allowEmptyValue != null) {
                        property.setAllowEmptyValue(allowEmptyValue);
                    }

                    if (property.getReadOnly() == null) {
                        if (isReadOnly) {
                            property.setReadOnly(isReadOnly);
                        }
                    }
                    if (mp != null) {
                        final AllowableValues allowableValues = AllowableValuesUtils.create(mp.allowableValues());
                        if (allowableValues != null) {
                            final Map<PropertyBuilder.PropertyId, Object> args = allowableValues.asPropertyArguments();
                            PropertyBuilder.merge(property, args);
                        }
                    }
                    JAXBAnnotationsHelper.apply(member, property);
                    applyBeanValidatorAnnotations(property, annotations);
                    props.add(property);
                }
            }
        }

        Collections.sort(props, getPropertyComparator());

        Map<String, Property> modelProps = new LinkedHashMap<String, Property>();
        for (Property prop : props) {
            modelProps.put(prop.getName(), prop);
        }
        model.setProperties(modelProps);

        /**
         * --Preventing parent/child hierarchy creation loops - Comment 2--
         * Creating a parent model will result in the creation of child models, as per the first If statement following
         * this comment. Creating a child model will result in the creation of a parent model, as per the second If
         * statement following this comment.
         *
         * The current model must be defined in the context immediately. This done to help prevent repeated
         * loops where  parents create children and children create parents when a hierarchy is present. This logic
         * works in conjunction with the "early checking" performed earlier in this method
         * (See "Preventing parent/child hierarchy creation loops - Comment 1"), to prevent repeated creation loops.
         *
         *
         * As an aside, defining the current model in the context immediately also ensures that child models are
         * available for modification by resolveSubtypes, when their parents are created.
         */
        Class<?> currentType = type.getRawClass();
        context.defineModel(name, model, currentType, null);

        /**
         * This must be done after model.setProperties so that the model's set
         * of properties is available to filter from any subtypes
         **/
        if (!resolveSubtypes(model, beanDesc, context)) {
            model.setDiscriminator(null);
        }

        if (apiModel != null) {
            /**
             * Check if the @ApiModel annotation has a parent property containing a value that should not be ignored
             */
            Class<?> parentClass = apiModel.parent();
            if (parentClass != null && !parentClass.equals(Void.class) && !this.shouldIgnoreClass(parentClass)) {
                JavaType parentType = _mapper.constructType(parentClass);
                final BeanDescription parentBeanDesc = _mapper.getSerializationConfig().introspect(parentType);

                /**
                 * Retrieve all the sub-types of the parent class and ensure that the current type is one of those types
                 */
                boolean currentTypeIsParentSubType = false;
                List<NamedType> subTypes = _intr.findSubtypes(parentBeanDesc.getClassInfo());
                if (subTypes != null) {
                    for (NamedType subType : subTypes) {
                        if (subType.getType().equals(currentType)) {
                            currentTypeIsParentSubType = true;
                            break;
                        }
                    }
                }

                /**
                 Retrieve the subTypes from the parent class @ApiModel annotation and ensure that the current type
                 is one of those types.
                 */
                boolean currentTypeIsParentApiModelSubType = false;
                final ApiModel parentApiModel = parentBeanDesc.getClassAnnotations().get(ApiModel.class);
                if (parentApiModel != null) {
                    Class<?>[] apiModelSubTypes = parentApiModel.subTypes();
                    if (apiModelSubTypes != null) {
                        for (Class<?> subType : apiModelSubTypes) {
                            if (subType.equals(currentType)) {
                                currentTypeIsParentApiModelSubType = true;
                                break;
                            }
                        }
                    }
                }

                /**
                 If the current type is a sub-type of the parent class and is listed in the subTypes property of the
                 parent class @ApiModel annotation, then do the following:
                 1. Resolve the model for the parent class. This will result in the parent model being created, and the
                 current child model being updated to be a ComposedModel referencing the parent.
                 2. Resolve and return the current child type again. This will return the new ComposedModel from the
                 context, which was created in step 1 above. Admittedly, there is a small chance that this may result
                 in a stack overflow, if the context does not correctly cache the model for the current type. However,
                 as context caching is assumed elsewhere to avoid cyclical model creation, this was deemed to be
                 sufficient.
                 */
                if (currentTypeIsParentSubType && currentTypeIsParentApiModelSubType) {
                    context.resolve(parentClass);
                    return context.resolve(currentType);
                }
            }
        }

        return model;
    }

    private enum GeneratorWrapper {
        PROPERTY(ObjectIdGenerators.PropertyGenerator.class) {
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                                                 ModelConverterContext context, ObjectMapper mapper) {
                /*
                 * When generator = ObjectIdGenerators.PropertyGenerator.class and
                 * @JsonIdentityReference(alwaysAsId = false) then property is serialized
                 * in the same way it is done without @JsonIdentityInfo annotation.
                 */
                return null;
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type,
                                           ModelConverterContext context, ObjectMapper mapper) {
                final BeanDescription beanDesc = mapper.getSerializationConfig().introspect(type);
                for (BeanPropertyDefinition def : beanDesc.findProperties()) {
                    final String name = def.getName();
                    if (name != null && name.equals(propertyName)) {
                        final AnnotatedMember propMember = def.getPrimaryMember();
                        final JavaType propType = propMember.getType(beanDesc.bindingsForBeanType());
                        if (PrimitiveType.fromType(propType) != null) {
                            return PrimitiveType.createProperty(propType);
                        } else {
                            return context.resolveProperty(propType,
                                    Iterables.toArray(propMember.annotations(), Annotation.class));
                        }
                    }
                }
                return null;
            }
        },
        INT(ObjectIdGenerators.IntSequenceGenerator.class) {
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                                                 ModelConverterContext context, ObjectMapper mapper) {
                Property id = new IntegerProperty();
                return process(id, propertyName, type, context);
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type,
                                           ModelConverterContext context, ObjectMapper mapper) {
                return new IntegerProperty();
            }
        },
        UUID(ObjectIdGenerators.UUIDGenerator.class) {
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                                                 ModelConverterContext context, ObjectMapper mapper) {
                Property id = new UUIDProperty();
                return process(id, propertyName, type, context);
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type,
                                           ModelConverterContext context, ObjectMapper mapper) {
                return new UUIDProperty();
            }
        },
        NONE(ObjectIdGenerators.None.class) {
            // When generator = ObjectIdGenerators.None.class property should be processed as normal property.
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                                                 ModelConverterContext context, ObjectMapper mapper) {
                return null;
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type,
                                           ModelConverterContext context, ObjectMapper mapper) {
                return null;
            }
        };

        private final Class<? extends ObjectIdGenerator> generator;

        GeneratorWrapper(Class<? extends ObjectIdGenerator> generator) {
            this.generator = generator;
        }

        protected abstract Property processAsProperty(String propertyName, JavaType type,
                                                      ModelConverterContext context, ObjectMapper mapper);

        protected abstract Property processAsId(String propertyName, JavaType type,
                                                ModelConverterContext context, ObjectMapper mapper);

        public static Property processJsonIdentity(JavaType type, ModelConverterContext context,
                                                   ObjectMapper mapper, JsonIdentityInfo identityInfo,
                                                   JsonIdentityReference identityReference) {
            final GeneratorWrapper wrapper = identityInfo != null ? getWrapper(identityInfo.generator()) : null;
            if (wrapper == null) {
                return null;
            }
            if (identityReference != null && identityReference.alwaysAsId()) {
                return wrapper.processAsId(identityInfo.property(), type, context, mapper);
            } else {
                return wrapper.processAsProperty(identityInfo.property(), type, context, mapper);
            }
        }

        private static GeneratorWrapper getWrapper(Class<?> generator) {
            for (GeneratorWrapper value : GeneratorWrapper.values()) {
                if (value.generator.isAssignableFrom(generator)) {
                    return value;
                }
            }
            return null;
        }

        private static Property process(Property id, String propertyName, JavaType type,
                                        ModelConverterContext context) {
            id.setName(propertyName);
            Model model = context.resolve(type);
            if (model instanceof ComposedModel) {
                model = ((ComposedModel) model).getChild();
            }
            if (model instanceof ModelImpl) {
                ModelImpl mi = (ModelImpl) model;
                mi.getProperties().put(propertyName, id);
                return new RefProperty(StringUtils.isNotEmpty(mi.getReference())
                        ? mi.getReference() : mi.getName());
            }
            return null;
        }
    }

    private void handleUnwrapped(List<Property> props, Model innerModel, String prefix, String suffix) {
        if (StringUtils.isBlank(suffix) && StringUtils.isBlank(prefix)) {
            props.addAll(innerModel.getProperties().values());
        } else {
            if (prefix == null) {
                prefix = "";
            }
            if (suffix == null) {
                suffix = "";
            }
            for (Property prop : innerModel.getProperties().values()) {
                props.add(prop.rename(prefix + prop.getName() + suffix));
            }
        }
    }

    public boolean resolveSubtypes(ModelImpl model, BeanDescription bean, ModelConverterContext context) {
        final List<NamedType> types = _intr.findSubtypes(bean.getClassInfo());

        if (types == null) {
            return false;
        }

        /**
         * As the introspector will find @JsonSubTypes for a child class that are present on its super classes, the
         * code segment below will also run the introspector on the parent class, and then remove any sub-types that are
         * found for the parent from the sub-types found for the child. The same logic all applies to implemented
         * interfaces, and is accounted for below.
         */
        removeSuperClassAndInterfaceSubTypes(types, bean);

        int count = 0;
        final Class<?> beanClass = bean.getClassInfo().getAnnotated();
        for (NamedType subtype : types) {
            final Class<?> subtypeType = subtype.getType();
            if (!beanClass.isAssignableFrom(subtypeType)) {
                continue;
            }

            final Model subtypeModel = context.resolve(subtypeType);

            if (subtypeModel instanceof ModelImpl) {
                final ModelImpl impl = (ModelImpl) subtypeModel;

                // check if model name was inherited
                if (impl.getName().equals(model.getName())) {
                    impl.setName(_typeNameResolver.nameForType(_mapper.constructType(subtypeType),
                            io.swagger.jackson.TypeNameResolver.Options.SKIP_API_MODEL));
                }

                // remove shared properties defined in the parent
                final Map<String, Property> baseProps = model.getProperties();
                final Map<String, Property> subtypeProps = impl.getProperties();
                if (baseProps != null && subtypeProps != null) {
                    for (Map.Entry<String, Property> entry : baseProps.entrySet()) {
                        if (entry.getValue().equals(subtypeProps.get(entry.getKey()))) {
                            subtypeProps.remove(entry.getKey());
                        }
                    }
                }

                impl.setDiscriminator(null);
                ComposedModel child = new ComposedModel().parent(new RefModel(model.getName())).child(impl);
                context.defineModel(impl.getName(), child, subtypeType, null);
                ++count;
            }
        }
        return count != 0;
    }

    private void removeSuperClassAndInterfaceSubTypes(List<NamedType> types, BeanDescription bean) {
        Class<?> beanClass = bean.getType().getRawClass();
        Class<?> superClass = beanClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            removeSuperSubTypes(types, superClass);
        }
        if (!types.isEmpty()) {
            Class<?>[] superInterfaces = beanClass.getInterfaces();
            for (Class<?> superInterface : superInterfaces) {
                removeSuperSubTypes(types, superInterface);
                if (types.isEmpty()) {
                    break;
                }
            }
        }
    }

    private void removeSuperSubTypes(List<NamedType> resultTypes, Class<?> superClass) {
        JavaType superType = _mapper.constructType(superClass);
        BeanDescription superBean = _mapper.getSerializationConfig().introspect(superType);
        final List<NamedType> superTypes = _intr.findSubtypes(superBean.getClassInfo());
        if (superTypes != null) {
            resultTypes.removeAll(superTypes);
        }
    }
}
