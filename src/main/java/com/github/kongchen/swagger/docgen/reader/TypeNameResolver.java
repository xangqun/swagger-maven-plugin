/**
 * Copyright 2017-2025 Evergrande Group.
 */
package com.github.kongchen.swagger.docgen.reader;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.annotations.ApiModel;
import io.swagger.util.PrimitiveType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author laixiangqun
 * @since 2018-6-15
 */
public class TypeNameResolver {

    public final static TypeNameResolver std = new TypeNameResolver();

    protected TypeNameResolver() {
    }

    public String nameForType(JavaType type, io.swagger.jackson.TypeNameResolver.Options... options) {
        return nameForType(type, options.length == 0 ? Collections.<io.swagger.jackson.TypeNameResolver.Options>emptySet() :
                EnumSet.copyOf(Arrays.asList(options)));
    }

    public String nameForType(JavaType type, Set<io.swagger.jackson.TypeNameResolver.Options> options) {
        if (type.hasGenericTypes()) {
            return nameForGenericType(type, options);
        }
        final String name = findStdName(type);
        return (name == null) ? nameForClass(type, options) : name;
    }

    protected String nameForClass(JavaType type, Set<io.swagger.jackson.TypeNameResolver.Options> options) {
        return nameForClass(type.getRawClass(), options);
    }

    protected String nameForClass(Class<?> cls, Set<io.swagger.jackson.TypeNameResolver.Options> options) {
        if (options.contains(io.swagger.jackson.TypeNameResolver.Options.SKIP_API_MODEL)) {
            return cls.getSimpleName();
        }
        final ApiModel model = cls.getAnnotation(ApiModel.class);
        final String modelName = model == null ? null : StringUtils.trimToNull(model.value());
        return modelName == null ? cls.getSimpleName() : modelName;
    }

    protected String nameForGenericType(JavaType type, Set<io.swagger.jackson.TypeNameResolver.Options> options) {
        final StringBuilder generic = new StringBuilder(nameForClass(type, options));
        final int count = type.containedTypeCount();
        for (int i = 0; i < count; ++i) {
            final JavaType arg = type.containedType(i);
            final String argName = PrimitiveType.fromType(arg) != null ? nameForClass(arg, options) :
                    nameForType(arg, options);
            generic.append("<").append(WordUtils.capitalize(argName)).append(">");
        }
        return generic.toString();
    }

    protected String findStdName(JavaType type) {
        return PrimitiveType.getCommonName(type);
    }

    public enum Options {
        SKIP_API_MODEL;
    }
}
