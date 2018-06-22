package com.github.kongchen.swagger.docgen.reader;

import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Property wrapper for response container.
 */
class ResponseContainerConverter {
    Property withResponseContainer(String responseContainer, Property property,Type... responseClass) {
        if ("list".equalsIgnoreCase(responseContainer)) {
            return new ArrayProperty(property);
        }
        if ("set".equalsIgnoreCase(responseContainer)) {
            return new ArrayProperty(property).uniqueItems();
        }
        if ("map".equalsIgnoreCase(responseContainer)) {
            return new MapProperty(property);
        }
        if("ResponseDto".equalsIgnoreCase(responseContainer)){
            if(property instanceof RefProperty){
                return new  RefProperty().asDefault(responseContainer+"<"+  ((RefProperty) property).getSimpleRef()+">");
            }else {
                if(responseClass.length>0){
                    if (responseClass[0].getTypeName().equals("java.util.Map")) {
                        return new  RefProperty().asDefault(responseContainer+"<map>");
                    }else if (responseClass[0].getTypeName().equals("java.util.List")){
                        return new  RefProperty().asDefault(responseContainer+"<List>");
                    }
                }
               else {
                    return new  RefProperty().asDefault(responseContainer+"<"+  property.getType()+">");
                }
            }
        }
        return property;
    }
}
