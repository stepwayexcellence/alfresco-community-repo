/*
 * #%L
 * Alfresco Remote API
 * %%
 * Copyright (C) 2005 - 2022 Alfresco Software Limited
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
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

package org.alfresco.rest.api.impl.validator.actions;

import static org.alfresco.rest.api.impl.validator.actions.ActionNodeParameterValidator.NO_PROPER_PERMISSIONS_FOR_NODE;
import static org.alfresco.rest.api.impl.validator.actions.ActionNodeParameterValidator.REQUIRE_READ_PERMISSION_PARAMS;
import static org.alfresco.service.cmr.dictionary.DataTypeDefinition.NODE_REF;
import static org.alfresco.service.cmr.dictionary.DataTypeDefinition.TEXT;
import static org.alfresco.service.cmr.repository.StoreRef.STORE_REF_WORKSPACE_SPACESSTORE;
import static org.alfresco.service.namespace.NamespaceService.DEFAULT_PREFIX;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Map;

import org.alfresco.repo.action.executer.CheckOutActionExecuter;
import org.alfresco.repo.action.executer.CopyActionExecuter;
import org.alfresco.repo.action.executer.ImageTransformActionExecuter;
import org.alfresco.repo.action.executer.ImporterActionExecuter;
import org.alfresco.repo.action.executer.LinkCategoryActionExecuter;
import org.alfresco.repo.action.executer.MailActionExecuter;
import org.alfresco.repo.action.executer.MoveActionExecuter;
import org.alfresco.repo.action.executer.ScriptActionExecuter;
import org.alfresco.repo.action.executer.SimpleWorkflowActionExecuter;
import org.alfresco.repo.action.executer.TransformActionExecuter;
import org.alfresco.rest.api.Actions;
import org.alfresco.rest.api.Nodes;
import org.alfresco.rest.api.model.ActionDefinition;
import org.alfresco.rest.api.model.rules.Action;
import org.alfresco.rest.framework.core.exceptions.EntityNotFoundException;
import org.alfresco.rest.framework.core.exceptions.PermissionDeniedException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.NamespaceService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ActionNodeParameterValidatorTest
{
    private static final String NODE_REF_PARAM = REQUIRE_READ_PERMISSION_PARAMS.get(0);
    private static final String WRITE_REQUIRED_NODE_REF_PARAM = "dummyNodeParam";
    private static final String NODE_ID = "node-id";
    private static final String DUMMY_ACTION = "dummy-action";

    @Mock
    private Actions actionsMock;
    @Mock
    private NamespaceService namespaceServiceMock;
    @Mock
    private Nodes nodesMock;
    @Mock
    private PermissionService permissionServiceMock;

    @InjectMocks
    private ActionNodeParameterValidator objectUnderTest;

    @Test
    public void testProperPermissionsForReadRights()
    {
        final Action action = new Action();
        action.setActionDefinitionId(DUMMY_ACTION);
        action.setParams(Map.of(NODE_REF_PARAM, NODE_ID));
        ActionDefinition.ParameterDefinition parameterDef =
                new ActionDefinition.ParameterDefinition(NODE_REF_PARAM, NODE_REF.toPrefixString(), false, true, null, null);
        final ActionDefinition actionDefinition =
                new ActionDefinition(DUMMY_ACTION, DUMMY_ACTION, null, null, null, false, false,
                        List.of(parameterDef));
        given(actionsMock.getActionDefinitionById(DUMMY_ACTION)).willReturn(actionDefinition);
        given(namespaceServiceMock.getPrefixes(NODE_REF.getNamespaceURI())).willReturn(List.of(DEFAULT_PREFIX));
        final NodeRef nodeRef = new NodeRef(STORE_REF_WORKSPACE_SPACESSTORE, NODE_ID);
        given(nodesMock.validateNode(NODE_ID)).willReturn(nodeRef);
        given(permissionServiceMock.hasReadPermission(nodeRef)).willReturn(AccessStatus.ALLOWED);

        //when
        objectUnderTest.validate(action);

        then(actionsMock).should().getActionDefinitionById(DUMMY_ACTION);
        then(actionsMock).shouldHaveNoMoreInteractions();
        then(namespaceServiceMock).should().getPrefixes(NODE_REF.getNamespaceURI());
        then(namespaceServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(NODE_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(permissionServiceMock).should().hasReadPermission(nodeRef);
        then(permissionServiceMock).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testNotEnoughPermissionsForReadRights()
    {
        final Action action = new Action();
        action.setActionDefinitionId(DUMMY_ACTION);
        action.setParams(Map.of(NODE_REF_PARAM, NODE_ID));
        ActionDefinition.ParameterDefinition parameterDef =
                new ActionDefinition.ParameterDefinition(NODE_REF_PARAM, NODE_REF.toPrefixString(), false, true, null, null);
        final ActionDefinition actionDefinition =
                new ActionDefinition(DUMMY_ACTION, DUMMY_ACTION, null, null, null, false, false,
                        List.of(parameterDef));
        given(actionsMock.getActionDefinitionById(DUMMY_ACTION)).willReturn(actionDefinition);
        given(namespaceServiceMock.getPrefixes(NODE_REF.getNamespaceURI())).willReturn(List.of(DEFAULT_PREFIX));
        final NodeRef nodeRef = new NodeRef(STORE_REF_WORKSPACE_SPACESSTORE, NODE_ID);
        given(nodesMock.validateNode(NODE_ID)).willReturn(nodeRef);
        given(permissionServiceMock.hasReadPermission(nodeRef)).willReturn(AccessStatus.DENIED);

        //when
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> objectUnderTest.validate(action));

        then(actionsMock).should().getActionDefinitionById(DUMMY_ACTION);
        then(actionsMock).shouldHaveNoMoreInteractions();
        then(namespaceServiceMock).should().getPrefixes(NODE_REF.getNamespaceURI());
        then(namespaceServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(NODE_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(permissionServiceMock).should().hasReadPermission(nodeRef);
        then(permissionServiceMock).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testValidateForNodeNotFound()
    {
        final Action action = new Action();
        action.setActionDefinitionId(DUMMY_ACTION);
        action.setParams(Map.of(NODE_REF_PARAM, NODE_ID));
        ActionDefinition.ParameterDefinition parameterDef =
                new ActionDefinition.ParameterDefinition(NODE_REF_PARAM, NODE_REF.toPrefixString(), false, true, null, null);
        final ActionDefinition actionDefinition =
                new ActionDefinition(DUMMY_ACTION, DUMMY_ACTION, null, null, null, false, false,
                        List.of(parameterDef));
        given(actionsMock.getActionDefinitionById(DUMMY_ACTION)).willReturn(actionDefinition);
        given(namespaceServiceMock.getPrefixes(NODE_REF.getNamespaceURI())).willReturn(List.of(DEFAULT_PREFIX));
        given(nodesMock.validateNode(NODE_ID)).willThrow(EntityNotFoundException.class);

        //when
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> objectUnderTest.validate(action));

        then(actionsMock).should().getActionDefinitionById(DUMMY_ACTION);
        then(actionsMock).shouldHaveNoMoreInteractions();
        then(namespaceServiceMock).should().getPrefixes(NODE_REF.getNamespaceURI());
        then(namespaceServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(NODE_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(permissionServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testProperPermissionsForWriteRights()
    {
        final Action action = new Action();
        action.setActionDefinitionId(DUMMY_ACTION);
        action.setParams(Map.of(WRITE_REQUIRED_NODE_REF_PARAM, NODE_ID));
        ActionDefinition.ParameterDefinition parameterDef =
                new ActionDefinition.ParameterDefinition(WRITE_REQUIRED_NODE_REF_PARAM, NODE_REF.toPrefixString(), false, true, null, null);
        final ActionDefinition actionDefinition =
                new ActionDefinition(DUMMY_ACTION, DUMMY_ACTION, null, null, null, false, false,
                        List.of(parameterDef));
        given(actionsMock.getActionDefinitionById(DUMMY_ACTION)).willReturn(actionDefinition);
        given(namespaceServiceMock.getPrefixes(NODE_REF.getNamespaceURI())).willReturn(List.of(DEFAULT_PREFIX));
        final NodeRef nodeRef = new NodeRef(STORE_REF_WORKSPACE_SPACESSTORE, NODE_ID);
        given(nodesMock.validateNode(NODE_ID)).willReturn(nodeRef);
        given(permissionServiceMock.hasReadPermission(nodeRef)).willReturn(AccessStatus.ALLOWED);
        given(permissionServiceMock.hasPermission(nodeRef, PermissionService.WRITE)).willReturn(AccessStatus.ALLOWED);

        //when
        objectUnderTest.validate(action);

        then(actionsMock).should().getActionDefinitionById(DUMMY_ACTION);
        then(actionsMock).shouldHaveNoMoreInteractions();
        then(namespaceServiceMock).should().getPrefixes(NODE_REF.getNamespaceURI());
        then(namespaceServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(NODE_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(permissionServiceMock).should().hasReadPermission(nodeRef);
        then(permissionServiceMock).should().hasPermission(nodeRef, PermissionService.WRITE);
        then(permissionServiceMock).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testNotEnoughPermissionsForWriteRights()
    {
        final Action action = new Action();
        action.setActionDefinitionId(DUMMY_ACTION);
        action.setParams(Map.of(WRITE_REQUIRED_NODE_REF_PARAM, NODE_ID));
        ActionDefinition.ParameterDefinition parameterDef =
                new ActionDefinition.ParameterDefinition(WRITE_REQUIRED_NODE_REF_PARAM, NODE_REF.toPrefixString(), false, true, null, null);
        final ActionDefinition actionDefinition =
                new ActionDefinition(DUMMY_ACTION, DUMMY_ACTION, null, null, null, false, false,
                        List.of(parameterDef));
        given(actionsMock.getActionDefinitionById(DUMMY_ACTION)).willReturn(actionDefinition);
        given(namespaceServiceMock.getPrefixes(NODE_REF.getNamespaceURI())).willReturn(List.of(DEFAULT_PREFIX));
        final NodeRef nodeRef = new NodeRef(STORE_REF_WORKSPACE_SPACESSTORE, NODE_ID);
        given(nodesMock.validateNode(NODE_ID)).willReturn(nodeRef);
        given(permissionServiceMock.hasReadPermission(nodeRef)).willReturn(AccessStatus.ALLOWED);
        given(permissionServiceMock.hasPermission(nodeRef, PermissionService.WRITE)).willReturn(AccessStatus.DENIED);

        //when
        assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> objectUnderTest.validate(action))
                .withMessageContaining(NO_PROPER_PERMISSIONS_FOR_NODE + NODE_ID);

        then(actionsMock).should().getActionDefinitionById(DUMMY_ACTION);
        then(actionsMock).shouldHaveNoMoreInteractions();
        then(namespaceServiceMock).should().getPrefixes(NODE_REF.getNamespaceURI());
        then(namespaceServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(NODE_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(permissionServiceMock).should().hasReadPermission(nodeRef);
        then(permissionServiceMock).should().hasPermission(nodeRef, PermissionService.WRITE);
        then(permissionServiceMock).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testNoValidationExecutedForNonNodeRefParam()
    {
        final Action action = new Action();
        action.setActionDefinitionId(DUMMY_ACTION);
        final String dummyParam = "dummyParam";
        action.setParams(Map.of(dummyParam, "dummyValue"));
        ActionDefinition.ParameterDefinition parameterDef =
                new ActionDefinition.ParameterDefinition(dummyParam, TEXT.toPrefixString(), false, true, null, null);
        final ActionDefinition actionDefinition =
                new ActionDefinition(DUMMY_ACTION, DUMMY_ACTION, null, null, null, false, false,
                        List.of(parameterDef));
        given(actionsMock.getActionDefinitionById(DUMMY_ACTION)).willReturn(actionDefinition);
        given(namespaceServiceMock.getPrefixes(NODE_REF.getNamespaceURI())).willReturn(List.of(DEFAULT_PREFIX));

        //when
        objectUnderTest.validate(action);

        then(actionsMock).should().getActionDefinitionById(DUMMY_ACTION);
        then(actionsMock).shouldHaveNoMoreInteractions();
        then(namespaceServiceMock).should().getPrefixes(NODE_REF.getNamespaceURI());
        then(namespaceServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).shouldHaveNoInteractions();
        then(permissionServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testGetDefinitionIds()
    {
        final List<String> expectedIds =
                List.of(CopyActionExecuter.NAME, MoveActionExecuter.NAME, CheckOutActionExecuter.NAME, ImporterActionExecuter.NAME,
                        LinkCategoryActionExecuter.NAME, MailActionExecuter.NAME, ScriptActionExecuter.NAME,
                        SimpleWorkflowActionExecuter.NAME, TransformActionExecuter.NAME, ImageTransformActionExecuter.NAME);
        final List<String> actualIds = objectUnderTest.getActionDefinitionIds();

        assertEquals(expectedIds, actualIds);
    }

    @Test
    public void testHasProperPriority()
    {
        final int expectedPriority = Integer.MIN_VALUE + 1;
        final int actualPriority = objectUnderTest.getPriority();

        assertEquals(expectedPriority, actualPriority);
    }
}
