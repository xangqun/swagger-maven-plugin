package com.github.kongchen.swagger.docgen.reader;

import io.swagger.annotations.*;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.*;
import io.swagger.models.Tag;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.ReflectionUtils;
import org.apache.maven.plugin.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

public class SwaggerReader extends AbstractReader implements ClassSwaggerReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SwaggerReader.class);
    private static final ResponseContainerConverter RESPONSE_CONTAINER_CONVERTER = new ResponseContainerConverter();
    private ParameterNameDiscoverer parameterNameDiscoverer =new LocalVariableTableParameterNameDiscoverer();
    public SwaggerReader(Swagger swagger, Log LOG) {
        super(swagger, LOG);
    }

    @Override
    public Swagger read(Set<Class<?>> classes) {
        for (Class cls : classes) {
            read(cls);
        }
        return swagger;
    }

    public Swagger getSwagger() {
        return swagger;
    }

    public Swagger read(Class cls) {
        return read(cls, "", null, false, new String[0], new String[0], new HashMap<String, Tag>(), new ArrayList<Parameter>());
    }

    protected Swagger read(Class<?> cls, String parentPath, String parentMethod, boolean readHidden, String[] parentConsumes, String[] parentProduces, Map<String, Tag> parentTags, List<Parameter> parentParameters) {
        if (swagger == null) {
            swagger = new Swagger();
        }
        Api api = AnnotationUtils.findAnnotation(cls, Api.class);
        String apiPath =api.basePath();

        // only read if allowing hidden apis OR api is not marked as hidden
        if (!canReadApi(readHidden, api)) {
            return swagger;
        }

        Map<String, Tag> tags = updateTagsForApi(parentTags, api);
        List<SecurityRequirement> securities = getSecurityRequirements(api);


        // parse the method
        for (Method method : cls.getMethods()) {
            ApiOperation apiOperation = AnnotationUtils.findAnnotation(method, ApiOperation.class);
            if (apiOperation == null || apiOperation.hidden()) {
                continue;
            }
            String methodPath = method.getName();

            String operationPath = getPath(apiPath, methodPath, parentPath);
            if (operationPath != null) {
                Map<String, String> regexMap = new HashMap<>();
                operationPath = parseOperationPath(operationPath, regexMap);

                String httpMethod = extractOperationMethod(apiOperation, method, SwaggerExtensions.chain());

                Operation operation = parseMethod(method);
                updateOperationParameters(parentParameters, regexMap, operation);
                updateOperationProtocols(apiOperation, operation);

                String[] apiConsumes = new String[0];
                String[] apiProduces = new String[0];

                apiConsumes = updateOperationConsumes(parentConsumes, apiConsumes, operation);
                apiProduces = updateOperationProduces(parentProduces, apiProduces, operation);

                handleSubResource(apiConsumes, httpMethod, apiProduces, tags, method, operationPath, operation);

                // can't continue without a valid http method
                httpMethod = (httpMethod == null) ? parentMethod : httpMethod;
                updateTagsForOperation(operation, apiOperation);
                updateOperation(apiConsumes, apiProduces, tags, securities, operation);
                updatePath(operationPath, httpMethod, operation);
            }
        }

        return swagger;
    }

    private void handleSubResource(String[] apiConsumes, String httpMethod, String[] apiProduces, Map<String, Tag> tags, Method method, String operationPath, Operation operation) {
        if (isSubResource(method)) {
            Class<?> responseClass = method.getReturnType();
            Swagger subSwagger = read(responseClass, operationPath, httpMethod, true, apiConsumes, apiProduces, tags, operation.getParameters());
        }
    }

    protected boolean isSubResource(Method method) {
        Class<?> responseClass = method.getReturnType();
        return (responseClass != null) && (AnnotationUtils.findAnnotation(responseClass, Api.class) != null);
    }

    private String getPath(String classLevelPath, String methodLevelPath, String parentPath) {
        if (classLevelPath == null && methodLevelPath == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        if (parentPath != null && !parentPath.isEmpty() && !parentPath.equals("/")) {
            if (!parentPath.startsWith("/")) {
                parentPath = "/" + parentPath;
            }
            if (parentPath.endsWith("/")) {
                parentPath = parentPath.substring(0, parentPath.length() - 1);
            }

            stringBuilder.append(parentPath);
        }
        if (classLevelPath != null) {
            stringBuilder.append(classLevelPath);
        }
        if (methodLevelPath != null && !methodLevelPath.equals("/")) {
            String methodPath = methodLevelPath;
            if (!methodPath.startsWith("/") && !stringBuilder.toString().endsWith("/")) {
                stringBuilder.append("/");
            }
            if (methodPath.endsWith("/")) {
                methodPath = methodPath.substring(0, methodPath.length() - 1);
            }
            stringBuilder.append(methodPath);
        }
        String output = stringBuilder.toString();
        if (!output.startsWith("/")) {
            output = "/" + output;
        }
        if (output.endsWith("/") && output.length() > 1) {
            return output.substring(0, output.length() - 1);
        } else {
            return output;
        }
    }


    public Operation parseMethod(Method method) {
        Operation operation = new Operation();
        ApiOperation apiOperation = AnnotationUtils.findAnnotation(method, ApiOperation.class);

        String operationId = method.getName();
        String responseContainer = null;

        Class<?> responseClass = null;
        Map<String, Property> defaultResponseHeaders = null;

        if (apiOperation != null) {
            if (apiOperation.hidden()) {
                return null;
            }
            if (!apiOperation.nickname().isEmpty()) {
                operationId = apiOperation.nickname();
            }

            defaultResponseHeaders = parseResponseHeaders(apiOperation.responseHeaders());
            operation.summary(apiOperation.value()).description(apiOperation.notes());

            Set<Map<String, Object>> customExtensions = parseCustomExtensions(apiOperation.extensions());
            if (customExtensions != null) {
                for (Map<String, Object> extension : customExtensions) {
                    if (extension == null) {
                        continue;
                    }
                    for (Map.Entry<String, Object> map : extension.entrySet()) {
                        operation.setVendorExtension(map.getKey().startsWith("x-") ? map.getKey() : "x-" + map.getKey(), map.getValue());
                    }
                }
            }

            if (!apiOperation.response().equals(Void.class)) {
                responseClass = apiOperation.response();
            }
            if (!apiOperation.responseContainer().isEmpty()) {
                responseContainer = apiOperation.responseContainer();
            }
            List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
            for (Authorization auth : apiOperation.authorizations()) {
                if (!auth.value().isEmpty()) {
                    SecurityRequirement security = new SecurityRequirement();
                    security.setName(auth.value());
                    for (AuthorizationScope scope : auth.scopes()) {
                        if (!scope.scope().isEmpty()) {
                            security.addScope(scope.scope());
                        }
                    }
                    securities.add(security);
                }
            }

            for (SecurityRequirement sec : securities) {
                operation.security(sec);
            }
        }
        operation.operationId(operationId);

        if (responseClass == null) {
            // pick out response from method declaration
            LOGGER.debug("picking up response class from method " + method);
            Type t = method.getGenericReturnType();
            responseClass = method.getReturnType();
            if (!responseClass.equals(Void.class) && !responseClass.equals(void.class)
                    && (AnnotationUtils.findAnnotation(responseClass, Api.class) == null)) {
                LOGGER.debug("reading model " + responseClass);
                Map<String, Model> models = ModelConverters.getInstance().readAll(t);
            }
        }
        if ((responseClass != null)
                && !responseClass.equals(Void.class)
                && !responseClass.equals(javax.ws.rs.core.Response.class)
                && (AnnotationUtils.findAnnotation(responseClass, Api.class) == null)) {
            if (isPrimitive(responseClass)) {
                Property property = ModelConverters.getInstance().readAsProperty(responseClass);
                if (property != null) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, property);

                    operation.response(apiOperation.code(), new Response()
                            .description("successful operation")
                            .schema(responseProperty)
                            .headers(defaultResponseHeaders));
                }
            } else if (!responseClass.equals(Void.class) && !responseClass.equals(void.class)) {
                Map<String, Model> models = ModelConverters.getInstance().read(responseClass);
                if (models.isEmpty()) {
                    Property p = ModelConverters.getInstance().readAsProperty(responseClass);
                    operation.response(apiOperation.code(), new Response()
                            .description("successful operation")
                            .schema(p)
                            .headers(defaultResponseHeaders));
                }
                for (String key : models.keySet()) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, new RefProperty().asDefault(key));


                    operation.response(apiOperation.code(), new Response()
                            .description("successful operation")
                            .schema(responseProperty)
                            .headers(defaultResponseHeaders));
                    swagger.model(key, models.get(key));
                }
                models = ModelConverters.getInstance().readAll(responseClass);
                for (Map.Entry<String, Model> entry : models.entrySet()) {
                    swagger.model(entry.getKey(), entry.getValue());
                }
            }
        }

        ApiResponses responseAnnotation = AnnotationUtils.findAnnotation(method, ApiResponses.class);
        if (responseAnnotation != null) {
            updateApiResponse(operation, responseAnnotation);
        }

        if (AnnotationUtils.findAnnotation(method, Deprecated.class) != null) {
            operation.deprecated(true);
        }

        // FIXME `hidden` is never used
        boolean hidden = false;
        if (apiOperation != null) {
            hidden = apiOperation.hidden();
        }

        // process parameters
        Class[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = findParamAnnotations(method);
        Map<String,ApiImplicitParam> stringApiImplicitParamMap = readImplicitParameterMap(method);
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        // paramTypes = method.getParameterTypes
        // genericParamTypes = method.getGenericParameterTypes
        for (int i = 0; i < parameterTypes.length; i++) {
            Type type = genericParameterTypes[i];
            List<Annotation> annotations =new ArrayList<>();
            annotations.addAll(Arrays.asList(paramAnnotations[i]));


            if(!stringApiImplicitParamMap.isEmpty()){
                ApiImplicitParam apiImplicitParam = stringApiImplicitParamMap.get(parameterNames[i]);
                if(apiImplicitParam!=null){
                    annotations.add(apiImplicitParam);
                }
            }
            List<Parameter> parameters = getParameters(type, annotations);
            for (Parameter parameter : parameters) {
                operation.parameter(parameter);
            }
        }
        if (operation.getResponses() == null) {
            operation.defaultResponse(new Response().description("successful operation"));
        }
        processOperationDecorator(operation, method);

        return operation;
    }

	private Annotation[][] findParamAnnotations(Method method) {
		Annotation[][] paramAnnotation = method.getParameterAnnotations();

		method = ReflectionUtils.getOverriddenMethod(method);
		while(method != null) {
			paramAnnotation = merge(paramAnnotation, method.getParameterAnnotations());
			method = ReflectionUtils.getOverriddenMethod(method);
		}
		return paramAnnotation;
	}


    private Annotation[][] merge(Annotation[][] paramAnnotation,
			Annotation[][] superMethodParamAnnotations) {
    	Annotation[][] mergedAnnotations = new Annotation[paramAnnotation.length][];

    	for(int i=0; i<paramAnnotation.length; i++) {
    		mergedAnnotations[i] = merge(paramAnnotation[i], superMethodParamAnnotations[i]);
    	}
		return mergedAnnotations;
	}

	private Annotation[] merge(Annotation[] annotations,
			Annotation[] annotations2) {
		Set<Annotation> mergedAnnotations = new HashSet<Annotation>();
		mergedAnnotations.addAll(Arrays.asList(annotations));
		mergedAnnotations.addAll(Arrays.asList(annotations2));
		return mergedAnnotations.toArray(new Annotation[0]);
	}

	public String extractOperationMethod(ApiOperation apiOperation, Method method, Iterator<SwaggerExtension> chain) {
        if (!apiOperation.httpMethod().isEmpty()) {
            return apiOperation.httpMethod().toLowerCase();
        }
        return null;
    }


}
