/**
 * Copyright 2014 Reverb Technologies, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wordnik.sample.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "Pet")
@ApiModel
public class Pet<T> {
    @ApiModelProperty(value = "id",name ="id")
    private PetId id;
    @ApiModelProperty(value = "Category类型",name ="id")
    private Category category;
    @ApiModelProperty(value = "宠物名",name ="name")
    private PetName name;
    @ApiModelProperty(value = "照片列表",name ="photoUrls")
    private List<String> photoUrls = new ArrayList<String>();
    @ApiModelProperty(value = "tag列表",name ="tags")
    private List<Tag> tags = new ArrayList<Tag>();
    @ApiModelProperty(value = "状态",name ="status")
    private String status;
    private T data;

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @XmlElement(name = "id")
    public PetId getId() {
        return id;
    }

    public void setId(PetId id) {
        this.id = id;
    }

    @XmlElement(name = "category")
    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    @XmlElement(name = "name")
    @ApiModelProperty(name = "name", example = "doggie", required = true, access = "exclude-when-jev-option-set")
    public PetName getName() {
        return name;
    }

    public void setName(PetName name) {
        this.name = name;
    }

    @XmlElementWrapper(name = "photoUrls")
    @XmlElement(name = "photoUrl", required = true)
    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls;
    }

    @XmlElementWrapper(name = "tags")
    @XmlElement(name = "tag")
    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    @XmlElement(name = "status")
    @ApiModelProperty(value = "pet status in the store", allowableValues = "available,pending,sold")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
