/*-
 * #%L
 * Alfresco Remote API
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.rest.api.search;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.alfresco.service.cmr.search.SearchService.LANGUAGE_CMIS_ALFRESCO;
import static org.alfresco.service.cmr.search.SearchService.LANGUAGE_FTS_ALFRESCO;
import static org.alfresco.service.cmr.search.SearchService.LANGUAGE_LUCENE;
import static org.junit.Assert.assertNull;
import org.alfresco.rest.api.search.impl.SearchMapper;
import org.alfresco.rest.api.search.model.Query;
import org.alfresco.rest.api.search.model.SearchQuery;
import org.alfresco.rest.api.search.model.SortDef;
import org.alfresco.rest.framework.core.exceptions.InvalidArgumentException;
import org.alfresco.rest.framework.resource.parameters.Paging;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.SearchParameters;
import org.junit.Test;

import java.util.Arrays;

/**
 * Tests the SearchMapper class
 *
 * @author Gethin James
 */
public class SearchMapperTests
{

    static SearchMapper searchMapper = new SearchMapper();
    static SerializerTestHelper helper = new SerializerTestHelper();

    @Test(expected = IllegalArgumentException.class)
    public void testMandatory() throws Exception
    {
        SearchParameters searchParameters = searchMapper.toSearchParameters(SearchQuery.EMPTY);
    }

    @Test
    public void toSearchParameters() throws Exception
    {
        SearchParameters searchParameters = searchMapper.toSearchParameters(minimalQuery());
        assertNotNull(searchParameters);

        //Test defaults
        assertEquals("There should be only 1 default store", 1,searchParameters.getStores().size());
        assertEquals("workspaces store is the default", StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, searchParameters.getStores().get(0));

        searchParameters = searchMapper.toSearchParameters(helper.searchQueryFromJson());
        assertNotNull(searchParameters);
    }

    @Test
    public void fromQuery() throws Exception
    {
        SearchParameters searchParameters = new SearchParameters();
        try
        {
            searchMapper.fromQuery(searchParameters, new Query(null,null, null));
            fail();
        } catch (IllegalArgumentException iae)
        {
            assertTrue(iae.getLocalizedMessage().contains("query is a mandatory parameter"));
        }

        Query q = new Query(null,"hello", null);

        try
        {
            searchMapper.fromQuery(searchParameters, q);
            fail();
        } catch (IllegalArgumentException iae)
        {
            assertTrue(iae.getLocalizedMessage().contains("language is a mandatory parameter"));
        }

        q = new Query("world", "hello", null);

        try
        {
            searchMapper.fromQuery(searchParameters, q);
            fail();
        } catch (InvalidArgumentException iae)
        {
            assertNotNull(iae);
            //world is not a valid language type
        }

        q = new Query("afts", "hello", null);
        searchMapper.fromQuery(searchParameters, q);
        assertEquals(LANGUAGE_FTS_ALFRESCO, searchParameters.getLanguage());

        q = new Query("cMiS", "hello", null);
        searchMapper.fromQuery(searchParameters, q);
        assertEquals(LANGUAGE_CMIS_ALFRESCO, searchParameters.getLanguage());

        q = new Query("LuCENE", "hello", null);
        searchMapper.fromQuery(searchParameters, q);
        assertEquals(LANGUAGE_LUCENE, searchParameters.getLanguage());

        assertEquals("hello", searchParameters.getQuery());

        q = new Query("LuCENE", "hello", "Heload");
        searchMapper.fromQuery(searchParameters, q);
        assertEquals("Heload", searchParameters.getSearchTerm());
    }

    @Test
    public void fromPaging() throws Exception
    {
        SearchParameters searchParameters = new SearchParameters();
        //Doesn't error
        searchMapper.fromPaging(searchParameters, null);
        Paging paging = Paging.DEFAULT;
        searchMapper.fromPaging(searchParameters, paging);
        assertEquals(searchParameters.getMaxItems(),paging.getMaxItems());
        assertEquals(searchParameters.getSkipCount(),paging.getSkipCount());
    }


    @Test
    public void fromSort() throws Exception
    {
        SearchParameters searchParameters = new SearchParameters();
        //Doesn't error
        searchMapper.fromSort(searchParameters, null);

        try
        {
            searchMapper.fromSort(searchParameters, Arrays.asList(new SortDef("wrongenum", null, false)));
            fail();
        }
        catch (InvalidArgumentException iae)
        {
            //wrongenum is illegal
            assertNotNull(iae);
        }

        searchMapper.fromSort(searchParameters, Arrays.asList(new SortDef("FIELD", "my", true), new SortDef("SCORE", null, false)));
        assertEquals(2 , searchParameters.getSortDefinitions().size());
        searchParameters.getSortDefinitions().forEach(sortDefinition ->
        {
            switch (sortDefinition.getSortType())
            {
                case FIELD:
                    assertEquals("my", sortDefinition.getField());
                    assertEquals(true, sortDefinition.isAscending());
                    break;
                case SCORE:
                    assertNull(sortDefinition.getField());
                    assertEquals(false, sortDefinition.isAscending());
                    break;
                default:
                    fail("Invalid sortDefinition");
            }
        });
    }

    @Test
    public void validateInclude() throws Exception
    {

        try
        {
            searchMapper.validateInclude(Arrays.asList("sausage"));
            fail();
        }
        catch (InvalidArgumentException iae)
        {
           //Sausage is illegal
           assertNotNull(iae);
        }

        searchMapper.validateInclude(null);

        try
        {
            searchMapper.validateInclude(Arrays.asList("AspectNames"));
            fail();
        }
        catch (InvalidArgumentException iae)
        {
            //Case sensitive
            assertNotNull(iae);
        }

        searchMapper.validateInclude(Arrays.asList("properties", "aspectNames"));
    }

    private SearchQuery minimalQuery()
    {
        Query query = new Query("cmis", "foo", "");
        SearchQuery sq = new SearchQuery(query,null, null, null);
        return sq;
    }
}
