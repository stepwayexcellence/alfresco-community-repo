/*
 * Copyright (C) 2005-2016 Alfresco Software Limited.
 *
 * This file is part of Alfresco
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
 */
package org.alfresco.rest.api.tests;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.rest.api.Queries;
import org.alfresco.rest.api.tests.client.HttpResponse;
import org.alfresco.rest.api.tests.client.PublicApiClient.Paging;
import org.alfresco.rest.api.tests.client.data.Document;
import org.alfresco.rest.api.tests.client.data.Node;
import org.alfresco.rest.api.tests.util.RestApiUtil;
import org.alfresco.service.cmr.security.MutableAuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * API tests for:
 * <ul>
 * <li> {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/queries} </li>
 * </ul>
 *
 * @author janv
 */
public class QueriesApiTest extends AbstractBaseApiTest
{
    private static final String URL_QUERIES_LSN = "queries/live-search-nodes";

    private String user1;
    private String user2;
    private List<String> users = new ArrayList<>();

    protected MutableAuthenticationService authenticationService;
    protected PersonService personService;

    private final String RUNID = System.currentTimeMillis()+"";

    @Before
    public void setup() throws Exception
    {
        authenticationService = applicationContext.getBean("authenticationService", MutableAuthenticationService.class);
        personService = applicationContext.getBean("personService", PersonService.class);

        // note: createUser currently relies on repoService
        user1 = createUser("user1-" + RUNID);
        user2 = createUser("user2-" + RUNID);

        // We just need to clean the on-premise-users,
        // so the tests for the specific network would work.
        users.add(user1);
        users.add(user2);
    }

    @After
    public void tearDown() throws Exception
    {
        AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
        for (final String user : users)
        {
            transactionHelper.doInTransaction(new RetryingTransactionCallback<Void>()
            {
                @Override
                public Void execute() throws Throwable
                {
                    if (personService.personExists(user))
                    {
                        authenticationService.deleteAuthentication(user);
                        personService.deletePerson(user);
                    }
                    return null;
                }
            });
        }
        users.clear();
        AuthenticationUtil.clearCurrentSecurityContext();
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue( Map<K, V> map )
    {
        List<Map.Entry<K, V>> list =
                new LinkedList<>( map.entrySet() );
        Collections.sort( list, new Comparator<Map.Entry<K, V>>()
        {
            public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
            {
                return (o1.getValue()).compareTo( o2.getValue() );
            }
        } );

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list)
        {
            result.put( entry.getKey(), entry.getValue() );
        }
        return result;
    }

    /**
     * Tests api for nodes live search
     *
     * <p>GET:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/queries/live-search-nodes}
     */
    @Test
    public void testLiveSearchNodes() throws Exception
    {
        int f1Count = 5;
        List<String> f1NodeIds = new ArrayList<>(f1Count);

        int f2Count = 3;
        List<String> f2NodeIds = new ArrayList<>(f2Count);

        int f3Count = 4;
        List<String> f3NodeIds = new ArrayList<>(f3Count);

        int totalCount = f1Count + f2Count + f3Count;
        List<String> allIds = new ArrayList<>(totalCount);

        String testTerm = "abc123";

        try
        {
            // As user 1 ...

            Paging paging = getPaging(0, 100);

            Map<String, String> params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);

            // Try to get nodes with search term 'abc123' - assume clean repo (ie. none to start with)
            HttpResponse response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            List<Node> nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            assertEquals(0, nodes.size());

            String myFolderNodeId = getMyNodeId(user1);

            String f1Id = createFolder(user1, myFolderNodeId, "folder 1").getId();
            String f2Id = createFolder(user1, myFolderNodeId, "folder 2").getId();
            String f3Id = createFolder(user1, myFolderNodeId, "folder 3").getId();

            String name = "name";
            String title = "title";
            String descrip = "descrip";

            String folderNameSuffix = " "+testTerm+" folder";

            Map<String,String> idNameMap = new HashMap<>();

            int nameIdx = f1Count;
            for (int i = 1; i <= f1Count; i++)
            {
                // create doc - in folder 1
                String contentText = "f1 " + testTerm + " test document " + user1 + " document " + i;

                String num = String.format("%05d", nameIdx);
                String docName = name+num+name;

                Map<String,String> docProps = new HashMap<>(2);
                docProps.put("cm:title", title+num+title);
                docProps.put("cm:description", descrip+num+descrip);

                Document doc = createTextFile(user1, f1Id, docName, contentText, "UTF-8", docProps);

                f1NodeIds.add(doc.getId());
                idNameMap.put(doc.getId(), docName);

                nameIdx--;
            }

            nameIdx = f2Count;
            for (int i = 1; i <= f2Count; i++)
            {
                // create doc - in folder 2
                String contentText = "f2 " + testTerm + " test document";

                String num = String.format("%05d", nameIdx);
                String docName = name+num+name;

                Map<String,String> docProps = new HashMap<>(2);
                docProps.put("cm:title", title+num+title);
                docProps.put("cm:description", descrip+num+descrip);

                Document doc = createTextFile(user1, f2Id, docName, contentText, "UTF-8", docProps);

                f2NodeIds.add(doc.getId());
                idNameMap.put(doc.getId(), docName);

                nameIdx--;
            }

            nameIdx = f3Count;
            for (int i = 1; i <= f3Count; i++)
            {
                // create folders - in folder 3
                String num = String.format("%05d", nameIdx);
                String folderName = name+num+name+folderNameSuffix;

                Node node = createFolder(user1, myFolderNodeId, folderName);

                f3NodeIds.add(node.getId());
                idNameMap.put(node.getId(), folderName);

                nameIdx--;
            }

            List<String> idsSortedByNameAsc = new ArrayList<>(sortByValue(idNameMap).keySet());

            List<String> idsSortedByNameDescCreatedAtAsc = new ArrayList<>(totalCount);
            for (int i = 0; i < totalCount; i++)
            {
                if (i < f1Count)
                {
                    idsSortedByNameDescCreatedAtAsc.add(f1NodeIds.get(i));
                }
                if (i < f2Count)
                {
                    idsSortedByNameDescCreatedAtAsc.add(f2NodeIds.get(i));
                }
                if (i < f3Count)
                {
                    idsSortedByNameDescCreatedAtAsc.add(f3NodeIds.get(i));
                }
            }

            allIds.addAll(idNameMap.keySet());

            //
            // find nodes
            //

            // Search hits based on FTS (content) and also name (in case of folder nodes)
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, allIds, null);

            // Search hits restricted by node type
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put(Queries.PARAM_NODE_TYPE, "cm:folder");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, f3NodeIds, null);

            // Search hits based on FTS (content) - with folder 2 as the root node (for path-based / in-tree search)
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put(Queries.PARAM_ROOT_NODE_ID, f2Id);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, f2NodeIds, null);

            // Search hits based on FTS (content) - with folder 1 as root node (for path-based / in-tree search)
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put(Queries.PARAM_ROOT_NODE_ID, f1Id);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, f1NodeIds, null);

            // Search hits based on cm:name
            String term = name+String.format("%05d", 1)+name;
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, "\""+term+"\"");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            assertEquals(3, nodes.size());
            for (Node node : nodes)
            {
                if (node.getIsFolder())
                {
                    assertEquals(term+folderNameSuffix, node.getName());
                }
                else
                {
                    assertEquals(term, node.getName());
                }
            }

            // Search hits based on cm:title
            term = title+String.format("%05d", 2)+title;
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, "\""+term+"\"");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            assertEquals(2, nodes.size());
            assertEquals(term, nodes.get(0).getProperties().get("cm:title"));
            assertEquals(term, nodes.get(1).getProperties().get("cm:title"));

            // Search hits based on cm:description
            term = descrip+String.format("%05d", 3)+descrip;
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, "\""+term+"\"");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            assertEquals(2, nodes.size());
            assertEquals(term, nodes.get(0).getProperties().get("cm:description"));
            assertEquals(term, nodes.get(1).getProperties().get("cm:description"));

            // test sort order

            // default sort order (modifiedAt desc)
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, allIds, false);

            // sort order - modifiedAt asc
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "modifiedAt asc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, allIds, false);

            // sort order - modifiedAt desc
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "modifiedAt desc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, allIds, true);

            // sort order - createdAt asc
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "createdAt asc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, allIds, true);

            // sort order - createdAt desc
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "createdAt desc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, allIds, false);

            // sort order - name asc
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "name asc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, idsSortedByNameAsc, true);

            // sort order - name desc
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "name desc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, idsSortedByNameAsc, false);

            // sort order - name desc, createdAt asc
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "name desc, createdAt asc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, idsSortedByNameDescCreatedAtAsc, false);

            // sort order - name asc, createdAt asc
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "name asc, createdAt desc");
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, idsSortedByNameDescCreatedAtAsc, true);

            // basic paging test

            paging = getPaging(0, 100);

            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, allIds, false);

            paging = getPaging(0, f1Count);
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, f1NodeIds, false);

            paging = getPaging(f1Count, f2Count);
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, f2NodeIds, false);

            paging = getPaging(f2Count, f3Count);
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, testTerm);
            response = getAll(URL_QUERIES_LSN, user1, paging, params, 200);
            nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Node.class);
            checkNodeIds(nodes, f3NodeIds, false);

            // TODO sanity check modifiedAt (for now modifiedAt=createdAt)
            // TODO sanity check tag search
            // TODO sanity check optional "include" ... eg. path

            // -ve test - no params (ie. no term)
            getAll(URL_QUERIES_LSN, user1, paging, null, 400);

            // -ve test - no term
            params = new HashMap<>(1);
            params.put(Queries.PARAM_ROOT_NODE_ID, f1Id);
            getAll(URL_QUERIES_LSN, user1, paging, params, 400);

            // -ve test - unknown root node id
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, "abc");
            params.put(Queries.PARAM_ROOT_NODE_ID, "dummy");
            getAll(URL_QUERIES_LSN, user1, paging, params, 404);

            // -ve test - unknown node type
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, "abc");
            params.put(Queries.PARAM_NODE_TYPE, "cm:dummy");
            getAll(URL_QUERIES_LSN, user1, paging, params, 400);

            // -ve test - term too short
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, "ab");
            getAll(URL_QUERIES_LSN, user1, paging, params, 400);

            // -ve test - term is still too short
            params = new HashMap<>(1);
            params.put(Queries.PARAM_TERM, "  \"a b *\"  ");
            getAll(URL_QUERIES_LSN, user1, paging, params, 400);

            // -ve test - invalid sort field
            params = new HashMap<>(2);
            params.put(Queries.PARAM_TERM, testTerm);
            params.put("orderBy", "invalid asc");
            getAll(URL_QUERIES_LSN, user1, paging, params, 400);

            // -ve test - unauthenticated - belts-and-braces ;-)
            getAll(URL_QUERIES_LSN, null, paging, params, 401);
        }
        finally
        {
            // some cleanup
            for (String docId : allIds)
            {
                delete(URL_NODES, user1, docId, 204);
            }
        }
    }

    private void checkNodeIds(List<Node> nodes, List<String> nodeIds, Boolean asc)
    {
        assertEquals(nodeIds.size(), nodes.size());

        if (asc == null)
        {
            // ignore order
            for (Node node : nodes)
            {
                assertTrue(nodeIds.contains(node.getId()));
            }
        }
        else if (asc)
        {
            int i = 0;
            for (Node node : nodes)
            {
                nodeIds.get(i).equals(node.getId());
                i++;
            }
        }
        else
        {
            int i = nodeIds.size() - 1;
            for (Node node : nodes)
            {
                nodeIds.get(i).equals(node.getId());
                i--;
            }
        }

    }

    @Override
    public String getScope()
    {
        return "public";
    }
}
