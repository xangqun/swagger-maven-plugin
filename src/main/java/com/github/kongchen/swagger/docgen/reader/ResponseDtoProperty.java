/**
 * Copyright 2017-2025 Evergrande Group.
 */
package com.github.kongchen.swagger.docgen.reader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.refs.GenericRef;
import io.swagger.models.refs.RefFormat;
import io.swagger.models.refs.RefType;

/**
 * @author laixiangqun
 * @since 2018-6-21
 */
public class ResponseDtoProperty extends RefProperty {
    private Property property;
    private GenericRef genericRef;
    private String responseContainer;

    public ResponseDtoProperty(Property property,String responseContainer) {
        this();
        this.property = property;
        this.responseContainer=responseContainer;
    }

    public ResponseDtoProperty() {
        setType(TYPE);
    }

    public static boolean isType(String type, String format) {
        if (TYPE.equals(type)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public RefProperty asDefault(String ref) {
        this.set$ref(RefType.DEFINITION.getInternalPrefix() + ref);
        return this;
    }
    @Override
    public RefProperty description(String description) {
        this.setDescription(description);
        return this;
    }

    @Override
    @JsonIgnore
    public String getType() {
        return this.type;
    }

    @Override
    @JsonIgnore
    public void setType(String type) {
        this.type = type;
    }
    @Override
    public String get$ref() {
        return genericRef.getRef();
    }
    @Override
    public void set$ref(String ref) {
        this.genericRef = new GenericRef(RefType.DEFINITION, ref);
    }
    @Override
    @JsonIgnore
    public RefFormat getRefFormat() {
        if (genericRef != null) {
            return this.genericRef.getFormat();
        } else {
            return null;
        }
    }
    @Override
    @JsonIgnore
    public String getSimpleRef() {
        if (genericRef != null) {
            return this.genericRef.getSimpleRef();
        } else {
            return null;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((genericRef == null) ? 0 : genericRef.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof ResponseDtoProperty)) {
            return false;
        }
        ResponseDtoProperty other = (ResponseDtoProperty) obj;
        if (genericRef == null) {
            if (other.genericRef != null) {
                return false;
            }
        } else if (!genericRef.equals(other.genericRef)) {
            return false;
        }
        return true;
    }
}
