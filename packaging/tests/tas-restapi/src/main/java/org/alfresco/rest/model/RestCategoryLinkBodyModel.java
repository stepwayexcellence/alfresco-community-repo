package org.alfresco.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.alfresco.rest.core.IRestModel;
import org.alfresco.utility.model.TestModel;

/**
 * Generated by 'mpichura' on '2022-12-01 13:41' from 'Alfresco Content Services REST API' swagger file 
 * Generated from 'Alfresco Content Services REST API' swagger file
 * Base Path {@linkplain /alfresco/api/-default-/public/alfresco/versions/1}
 */
public class RestCategoryLinkBodyModel extends TestModel implements IRestModel<RestCategoryLinkBodyModel>
{
    @JsonProperty(value = "entry")
    RestCategoryLinkBodyModel model;

    @Override
    public RestCategoryLinkBodyModel onModel()
    {
        return model;
    }

    /**
    The identifier of the category.
    */	        

    @JsonProperty(required = true)    
    private String categoryId;

    public String getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(String categoryId)
    {
        this.categoryId = categoryId;
    }
}
 
