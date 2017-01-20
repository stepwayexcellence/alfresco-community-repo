/*
 * #%L
 * Alfresco Records Management Module
 * %%
 * Copyright (C) 2005 - 2017 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * -
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 * -
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * -
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * -
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.rest.core;

import org.alfresco.rest.rm.community.requests.igCoreAPI.RestIGCoreAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Extends {@link RestWrapper} in order to call IG APIs with our own properties
 *
 * @author Tuna Aksoy
 * @since 2.6
 */
@Primary
@Service
@Scope(value = "prototype")
public class RMRestWrapper extends RestWrapper
{
    @Autowired
    private RMRestProperties rmRestProperties;

    public RestIGCoreAPI withIGCoreAPI()
    {
        return new RestIGCoreAPI(this, rmRestProperties);
    }

    @Override
    public <T> T processModel(Class<T> classz, RestRequest restRequest)
    {
        try
        {
            return super.processModel(classz, restRequest);
        }
        catch (Exception e)
        {
            // TODO Hopefully remove this check when TAS stops using checked exceptions.
            // See https://gitlab.alfresco.com/tas/alfresco-tas-restapi-test/merge_requests/392
            throw new RuntimeException(e);
        }
    }
}
