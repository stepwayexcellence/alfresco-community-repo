/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.repo.workflow.jbpm;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.ZipInputStream;

import org.alfresco.i18n.I18NUtil;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.workflow.BPMEngine;
import org.alfresco.repo.workflow.TaskComponent;
import org.alfresco.repo.workflow.WorkflowComponent;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.AspectDefinition;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.WorkflowDefinition;
import org.alfresco.service.cmr.workflow.WorkflowDeployment;
import org.alfresco.service.cmr.workflow.WorkflowException;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowNode;
import org.alfresco.service.cmr.workflow.WorkflowPath;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.cmr.workflow.WorkflowTaskDefinition;
import org.alfresco.service.cmr.workflow.WorkflowTaskState;
import org.alfresco.service.cmr.workflow.WorkflowTransition;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.proxy.HibernateProxy;
import org.jbpm.JbpmContext;
import org.jbpm.JbpmException;
import org.jbpm.db.GraphSession;
import org.jbpm.db.TaskMgmtSession;
import org.jbpm.graph.def.Node;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.def.Transition;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.graph.exe.Token;
import org.jbpm.jpdl.par.ProcessArchive;
import org.jbpm.jpdl.xml.JpdlXmlReader;
import org.jbpm.jpdl.xml.Problem;
import org.jbpm.taskmgmt.def.Task;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.springframework.util.StringUtils;
import org.springmodules.workflow.jbpm31.JbpmCallback;
import org.springmodules.workflow.jbpm31.JbpmTemplate;
import org.xml.sax.InputSource;


/**
 * JBoss JBPM based implementation of:
 * 
 * Workflow Definition Component
 * Workflow Component
 * Task Component
 * 
 * @author davidc
 */
public class JBPMEngine extends BPMEngine
    implements WorkflowComponent, TaskComponent
{
    // Implementation dependencies
    protected DictionaryService dictionaryService;
    protected NamespaceService namespaceService;
    protected NodeService nodeService;
    protected ServiceRegistry serviceRegistry;
    protected PersonService personService;
    private JbpmTemplate jbpmTemplate;

    // Note: jBPM query which is not provided out-of-the-box
    // TODO: Check jBPM 3.2 and get this implemented in jBPM
    private final static String COMPLETED_TASKS_QUERY =     
        "select ti " + 
        "from org.jbpm.taskmgmt.exe.TaskInstance as ti " +
        "where ti.actorId = :actorId " +
        "and ti.isOpen = false " +
        "and ti.end is not null";
    
    // I18N labels
    private final static String TITLE_LABEL = "title";
    private final static String DESC_LABEL = "description";
    private final static String DEFAULT_TRANSITION_LABEL = "bpm_businessprocessmodel.transition";    
    
    
    /**
     * Sets the JBPM Template used for accessing JBoss JBPM in the correct context
     * 
     * @param jbpmTemplate
     */
    public void setJBPMTemplate(JbpmTemplate jbpmTemplate)
    {
        this.jbpmTemplate = jbpmTemplate;
    }
    
    /**
     * Sets the Dictionary Service
     * 
     * @param dictionaryService
     */
    public void setDictionaryService(DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }

    /**
     * Sets the Namespace Service
     * 
     * @param namespaceService
     */
    public void setNamespaceService(NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * Sets the Node Service
     * 
     * @param nodeService
     */
    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * Sets the Person Service
     * 
     * @param personService
     */
    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
    }
    
    /**
     * Sets the Service Registry
     *  
     * @param serviceRegistry
     */
    public void setServiceRegistry(ServiceRegistry serviceRegistry)
    {
        this.serviceRegistry = serviceRegistry;
    }

    
    //
    // Workflow Definition...
    //
    
    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowDefinitionComponent#deployDefinition(java.io.InputStream)
     */
    public WorkflowDeployment deployDefinition(final InputStream workflowDefinition, final String mimetype)
    {
        try
        {
            return (WorkflowDeployment)jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // construct process definition
                    CompiledProcessDefinition compiledDef = compileProcessDefinition(workflowDefinition, mimetype);
                    
                    // deploy the parsed definition
                    context.deployProcessDefinition(compiledDef.def);
                    
                    // return deployed definition
                    WorkflowDeployment workflowDeployment = createWorkflowDeployment(compiledDef);
                    return workflowDeployment;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to deploy workflow definition", e);
        }
    }

            
    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowDefinitionComponent#isDefinitionDeployed(java.io.InputStream, java.lang.String)
     */
    public boolean isDefinitionDeployed(final InputStream workflowDefinition, final String mimetype)
    {
        try
        {
            return (Boolean) jbpmTemplate.execute(new JbpmCallback()
            {
                public Boolean doInJbpm(JbpmContext context)
                {
                    // create process definition from input stream
                    CompiledProcessDefinition processDefinition = compileProcessDefinition(workflowDefinition, mimetype);
                    
                    // retrieve process definition from Alfresco Repository
                    GraphSession graphSession = context.getGraphSession();
                    ProcessDefinition existingDefinition = graphSession.findLatestProcessDefinition(processDefinition.def.getName());
                    return (existingDefinition == null) ? false : true;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to determine if workflow definition is already deployed", e);
        }
    }
    
    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowDefinitionComponent#undeployDefinition(java.lang.String)
     */
    public void undeployDefinition(final String workflowDefinitionId)
    {
        try
        {
            jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // retrieve process definition
                    GraphSession graphSession = context.getGraphSession();
                    ProcessDefinition processDefinition = graphSession.loadProcessDefinition(getJbpmId(workflowDefinitionId));
                    // NOTE: if not found, should throw an exception
                    
                    // undeploy
                    // NOTE: jBPM deletes all "in-flight" processes too
                    // TODO: Determine if there's a safer undeploy we can expose via the WorkflowService contract
                    graphSession.deleteProcessDefinition(processDefinition);
                    
                    // we're done
                    return null;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to deploy workflow definition", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowDefinitionComponent#getDefinitions()
     */
    @SuppressWarnings("unchecked")
    public List<WorkflowDefinition> getDefinitions()
    {
        try
        {
            return (List<WorkflowDefinition>)jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    GraphSession graphSession = context.getGraphSession();
                    List<ProcessDefinition> processDefs = (List<ProcessDefinition>)graphSession.findLatestProcessDefinitions();
                    List<WorkflowDefinition> workflowDefs = new ArrayList<WorkflowDefinition>(processDefs.size());
                    for (ProcessDefinition processDef : processDefs)
                    {
                        WorkflowDefinition workflowDef = createWorkflowDefinition(processDef);
                        workflowDefs.add(workflowDef);
                    }
                    return workflowDefs;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to retrieve workflow definitions", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowDefinitionComponent#getDefinitionById(java.lang.String)
     */
    public WorkflowDefinition getDefinitionById(String workflowDefinitionId)
    {
        // TODO
        throw new UnsupportedOperationException();
    }

    
    //
    // Workflow Instance Management...
    //

    
    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowComponent#startWorkflow(java.lang.String, java.util.Map)
     */
    @SuppressWarnings("unchecked")
    public WorkflowPath startWorkflow(final String workflowDefinitionId, final Map<QName, Serializable> parameters)
    {
        try
        {
            return (WorkflowPath) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // initialise jBPM actor (for any processes that wish to record the initiator)
                    context.setActorId(AuthenticationUtil.getCurrentUserName());
                    
                    // construct a new process
                    GraphSession graphSession = context.getGraphSession();
                    ProcessDefinition processDefinition = graphSession.loadProcessDefinition(getJbpmId(workflowDefinitionId));
                    ProcessInstance processInstance = new ProcessInstance(processDefinition);
                    Token token = processInstance.getRootToken();

                    // create the start task if one exists
                    Task startTask = processInstance.getTaskMgmtInstance().getTaskMgmtDefinition().getStartTask();
                    if (startTask != null)
                    {
                        TaskInstance taskInstance = processInstance.getTaskMgmtInstance().createStartTaskInstance();
                        setTaskProperties(taskInstance, parameters);
                        token = taskInstance.getToken();
                    }

                    // Save the process instance along with the task instance
                    context.save(processInstance);
                    return createWorkflowPath(token);
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to start workflow " + workflowDefinitionId, e);
        }
    }
    
    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowComponent#getActiveWorkflows(java.lang.String)
     */    
    @SuppressWarnings("unchecked")
    public List<WorkflowInstance> getActiveWorkflows(final String workflowDefinitionId)
    {
        try
        {
            return (List<WorkflowInstance>) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    GraphSession graphSession = context.getGraphSession();
                    List<ProcessInstance> processInstances = graphSession.findProcessInstances(getJbpmId(workflowDefinitionId));
                    List<WorkflowInstance> workflowInstances = new ArrayList<WorkflowInstance>(processInstances.size());
                    for (ProcessInstance processInstance : processInstances)
                    {
                        if (!processInstance.hasEnded())
                        {
                            WorkflowInstance workflowInstance = createWorkflowInstance(processInstance);
                            workflowInstances.add(workflowInstance);
                        }
                    }
                    return workflowInstances;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to retrieve workflow instances for definition '" + workflowDefinitionId + "'", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowComponent#getWorkflowPaths(java.lang.String)
     */
    @SuppressWarnings("unchecked")
    public List<WorkflowPath> getWorkflowPaths(final String workflowId)
    {
        try
        {
            return (List<WorkflowPath>) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // retrieve process instance
                    GraphSession graphSession = context.getGraphSession();
                    ProcessInstance processInstance = graphSession.loadProcessInstance(getJbpmId(workflowId));
                    
                    // convert jBPM tokens to workflow posisitons
                    List<Token> tokens = processInstance.findAllTokens();
                    List<WorkflowPath> paths = new ArrayList<WorkflowPath>(tokens.size());
                    for (Token token : tokens)
                    {
                        if (!token.hasEnded())
                        {
                            WorkflowPath path = createWorkflowPath(token);
                            paths.add(path);
                        }
                    }
                    
                    return paths;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to retrieve workflow paths for workflow instance '" + workflowId + "'", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowComponent#cancelWorkflow(java.lang.String)
     */
    public WorkflowInstance cancelWorkflow(final String workflowId)
    {
        try
        {
            return (WorkflowInstance) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // retrieve and cancel process instance
                    GraphSession graphSession = context.getGraphSession();
                    ProcessInstance processInstance = graphSession.loadProcessInstance(getJbpmId(workflowId));
                    processInstance.end();

                    // save the process instance along with the task instance
                    context.save(processInstance);
                    return createWorkflowInstance(processInstance);
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to cancel workflow instance '" + workflowId + "'", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowComponent#signal(java.lang.String, java.lang.String)
     */
    public WorkflowPath signal(final String pathId, final String transition)
    {
        try
        {
            return (WorkflowPath) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // retrieve jBPM token for workflow position
                    GraphSession graphSession = context.getGraphSession();
                    Token token = getWorkflowToken(graphSession, pathId);
                    
                    // signal the transition
                    if (transition == null)
                    {
                        token.signal();
                    }
                    else
                    {
                        Node node = token.getNode();
                        if (!node.hasLeavingTransition(transition))
                        {
                            throw new WorkflowException("Transition '" + transition + "' is invalid for Workflow path '" + pathId + "'");
                        }
                        token.signal(transition);
                    }
                    
                    // save
                    ProcessInstance processInstance = token.getProcessInstance();
                    context.save(processInstance);
                    
                    // return new workflow path
                    return createWorkflowPath(token);
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to signal transition '" + transition + "' from workflow path '" + pathId + "'", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.WorkflowComponent#getTasksForWorkflowPath(java.lang.String)
     */
    @SuppressWarnings("unchecked")
    public List<WorkflowTask> getTasksForWorkflowPath(final String pathId)
    {
        try
        {
            return (List<WorkflowTask>) jbpmTemplate.execute(new JbpmCallback()
            {
                public List<WorkflowTask> doInJbpm(JbpmContext context)
                {
                    // retrieve tasks at specified workflow path
                    GraphSession graphSession = context.getGraphSession();
                    Token token = getWorkflowToken(graphSession, pathId);
                    TaskMgmtSession taskSession = context.getTaskMgmtSession();
                    List<TaskInstance> tasks = taskSession.findTaskInstancesByToken(token.getId());
                    List<WorkflowTask> workflowTasks = new ArrayList<WorkflowTask>(tasks.size());
                    for (TaskInstance task : tasks)
                    {
                        WorkflowTask workflowTask = createWorkflowTask(task);
                        workflowTasks.add(workflowTask);
                    }
                    return workflowTasks;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to retrieve tasks assigned at Workflow path '" + pathId + "'", e);
        }
    }

    
    //
    // Task Management ...
    //
    
    
    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.TaskComponent#getAssignedTasks(java.lang.String, org.alfresco.service.cmr.workflow.WorkflowTaskState)
     */
    @SuppressWarnings("unchecked")
    public List<WorkflowTask> getAssignedTasks(final String authority, final WorkflowTaskState state)
    {
        try
        {
            return (List<WorkflowTask>) jbpmTemplate.execute(new JbpmCallback()
            {
                public List<WorkflowTask> doInJbpm(JbpmContext context)
                {
                    // retrieve tasks assigned to authority
                    List<TaskInstance> tasks;
                    if (state.equals(WorkflowTaskState.IN_PROGRESS))
                    {
                        TaskMgmtSession taskSession = context.getTaskMgmtSession();
                        tasks = taskSession.findTaskInstances(authority);
                    }
                    else
                    {
                        // Note: This method is not implemented by jBPM
                        tasks = findCompletedTaskInstances(context, authority);
                    }
                    
                    // convert tasks to appropriate service response format 
                    List<WorkflowTask> workflowTasks = new ArrayList<WorkflowTask>(tasks.size());
                    for (TaskInstance task : tasks)
                    {
                        WorkflowTask workflowTask = createWorkflowTask(task);
                        workflowTasks.add(workflowTask);
                    }
                    return workflowTasks;
                }
                
                /**
                 * Gets the completed task list for the specified actor
                 * 
                 * TODO: This method provides a query that's not in JBPM!  Look to have JBPM implement this.
                 * 
                 * @param jbpmContext  the jbpm context
                 * @param actorId  the actor to retrieve tasks for
                 * @return  the tasks
                 */
                private List findCompletedTaskInstances(JbpmContext jbpmContext, String actorId)
                {
                    List result = null;
                    try
                    {
                        Session session = jbpmContext.getSession();
                        Query query = session.createQuery(COMPLETED_TASKS_QUERY);
                        query.setString("actorId", actorId);
                        result = query.list();
                    }
                    catch (Exception e)
                    {
                        throw new JbpmException("Couldn't get completed task instances list for actor '" + actorId + "'", e);
                    }
                    return result;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to retrieve tasks assigned to authority '" + authority + "' in state '" + state + "'", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.TaskComponent#getPooledTasks(java.util.List)
     */
    @SuppressWarnings("unchecked")
    public List<WorkflowTask> getPooledTasks(final List<String> authorities)
    {
        try
        {
            return (List<WorkflowTask>) jbpmTemplate.execute(new JbpmCallback()
            {
                public List<WorkflowTask> doInJbpm(JbpmContext context)
                {
                    // retrieve pooled tasks for specified authorities
                    TaskMgmtSession taskSession = context.getTaskMgmtSession();
                    List<TaskInstance> tasks = taskSession.findPooledTaskInstances(authorities);
                    List<WorkflowTask> workflowTasks = new ArrayList<WorkflowTask>(tasks.size());
                    for (TaskInstance task : tasks)
                    {
                        WorkflowTask workflowTask = createWorkflowTask(task);
                        workflowTasks.add(workflowTask);
                    }
                    return workflowTasks;
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to retrieve pooled tasks for authorities '" + authorities + "'", e);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.TaskComponent#updateTask(java.lang.String, java.util.Map, java.util.Map, java.util.Map)
     */
    public WorkflowTask updateTask(final String taskId, final Map<QName, Serializable> properties, final Map<QName, List<NodeRef>> add, final Map<QName, List<NodeRef>> remove)
    {
        try
        {
            return (WorkflowTask) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // retrieve task
                    TaskMgmtSession taskSession = context.getTaskMgmtSession();
                    TaskInstance taskInstance = taskSession.loadTaskInstance(getJbpmId(taskId));

                    // create properties to set on task instance
                    Map<QName, Serializable> newProperties = properties;
                    if (newProperties == null && (add != null || remove != null))
                    {
                        newProperties = new HashMap<QName, Serializable>(10); 
                    }
                    
                    if (add != null || remove != null)
                    {
                        Map<QName, Serializable> existingProperties = getTaskProperties(taskInstance);
                        
                        if (add != null)
                        {
                            // add new associations
                            for (Entry<QName, List<NodeRef>> toAdd : add.entrySet())
                            {
                                // retrieve existing list of noderefs for association
                                List<NodeRef> existingAdd = (List<NodeRef>)newProperties.get(toAdd.getKey());
                                if (existingAdd == null)
                                {
                                    existingAdd = (List<NodeRef>)existingProperties.get(toAdd.getKey());
                                }
    
                                // make the additions
                                if (existingAdd == null)
                                {
                                    newProperties.put(toAdd.getKey(), (Serializable)toAdd.getValue());
                                }
                                else
                                {
                                    for (NodeRef nodeRef : (List<NodeRef>)toAdd.getValue())
                                    {
                                        if (!(existingAdd.contains(nodeRef)))
                                        {
                                            existingAdd.add(nodeRef);
                                        }
                                    }
                                }
                            }
                        }

                        if (remove != null)
                        {
                            // add new associations
                            for (Entry<QName, List<NodeRef>> toRemove: remove.entrySet())
                            {
                                // retrieve existing list of noderefs for association
                                List<NodeRef> existingRemove = (List<NodeRef>)newProperties.get(toRemove.getKey());
                                if (existingRemove == null)
                                {
                                    existingRemove = (List<NodeRef>)existingProperties.get(toRemove.getKey());
                                }
    
                                // make the subtractions
                                if (existingRemove != null)
                                {
                                    for (NodeRef nodeRef : (List<NodeRef>)toRemove.getValue())
                                    {
                                        existingRemove.remove(nodeRef);
                                    }
                                }
                            }
                        }
                    }
                    
                    // update the task
                    if (newProperties != null)
                    {
                        setTaskProperties(taskInstance, newProperties);
                        
                        // save
                        ProcessInstance processInstance = taskInstance.getToken().getProcessInstance();
                        context.save(processInstance);
                    }
                    
                    // note: the ending of a task may not have signalled (i.e. more than one task exists at
                    //       this node)
                    return createWorkflowTask(taskInstance);
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to update workflow task '" + taskId + "'", e);
        }        
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.TaskComponent#startTask(java.lang.String)
     */
    public WorkflowTask startTask(String taskId)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.TaskComponent#suspendTask(java.lang.String)
     */
    public WorkflowTask suspendTask(String taskId)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.TaskComponent#endTask(java.lang.String, java.lang.String)
     */
    public WorkflowTask endTask(final String taskId, final String transition)
    {
        try
        {
            return (WorkflowTask) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // retrieve task
                    TaskMgmtSession taskSession = context.getTaskMgmtSession();
                    TaskInstance taskInstance = taskSession.loadTaskInstance(getJbpmId(taskId));
                    
                    // signal the transition on the task
                    if (transition == null)
                    {
                        taskInstance.end();
                    }
                    else
                    {
                        Node node = taskInstance.getToken().getNode();
                        if (node.getLeavingTransition(transition) == null)
                        {
                            throw new WorkflowException("Transition '" + transition + "' is invalid for Workflow task '" + taskId + "'");
                        }
                        taskInstance.end(transition);
                    }
                    
                    // save
                    ProcessInstance processInstance = taskInstance.getToken().getProcessInstance();
                    context.save(processInstance);
                    
                    // note: the ending of a task may not have signalled (i.e. more than one task exists at
                    //       this node)
                    return createWorkflowTask(taskInstance);
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to signal transition '" + transition + "' from workflow task '" + taskId + "'", e);
        }        
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.TaskComponent#getTaskById(java.lang.String)
     */
    public WorkflowTask getTaskById(final String taskId)
    {
        try
        {
            return (WorkflowTask) jbpmTemplate.execute(new JbpmCallback()
            {
                public Object doInJbpm(JbpmContext context)
                {
                    // retrieve task
                    TaskMgmtSession taskSession = context.getTaskMgmtSession();
                    TaskInstance taskInstance = taskSession.loadTaskInstance(getJbpmId(taskId));
                    return createWorkflowTask(taskInstance);
                }
            });
        }
        catch(JbpmException e)
        {
            throw new WorkflowException("Failed to retrieve task '" + taskId + "'", e);
        }        
    }
    
    
    //
    // Helpers...
    //
    

    /**
     * Process Definition with accompanying problems
     */
    private static class CompiledProcessDefinition
    {
        public CompiledProcessDefinition(ProcessDefinition def, List<Problem> problems)
        {
            this.def = def;
            this.problems = new String[problems.size()];
            int i = 0;
            for (Problem problem : problems)
            {
                this.problems[i++] = problem.toString();
            }
        }
        
        protected ProcessDefinition def;
        protected String[] problems;
    }

    /**
     * JpdlXmlReader with access to problems encountered during compile.
     * 
     * @author davidc
     */
    private static class JBPMEngineJpdlXmlReader extends JpdlXmlReader
    {
        private static final long serialVersionUID = -753730152120696221L;

        public JBPMEngineJpdlXmlReader(InputStream inputStream)
        {
            super(new InputSource(inputStream));
        }

        /**
         * Gets the problems
         * 
         * @return  problems
         */
        public List getProblems()
        {
            return problems;
        }
    }
    
    
    /**
     * Construct a Process Definition from the provided Process Definition stream
     * 
     * @param workflowDefinition  stream to create process definition from  
     * @param mimetype  mimetype of stream
     * @return  process definition
     */
    @SuppressWarnings("unchecked")
    protected CompiledProcessDefinition compileProcessDefinition(InputStream definitionStream, String mimetype)
    {
        String actualMimetype = (mimetype == null) ? MimetypeMap.MIMETYPE_ZIP : mimetype;
        CompiledProcessDefinition compiledDef = null;
        
        // parse process definition from jBPM process archive file
        
        if (actualMimetype.equals(MimetypeMap.MIMETYPE_ZIP))
        {
            ZipInputStream zipInputStream = null;
            try
            {
                zipInputStream = new ZipInputStream(definitionStream);
                ProcessArchive reader = new ProcessArchive(zipInputStream);
                ProcessDefinition def = reader.parseProcessDefinition();
                compiledDef = new CompiledProcessDefinition(def, reader.getProblems());
            }
            catch(Exception e)
            {
                throw new JbpmException("Failed to parse process definition from jBPM zip archive stream", e);
            }
            finally
            {
                if (zipInputStream != null)
                {
                    try { zipInputStream.close(); } catch(IOException e) {};
                }
            }
        }
        
        // parse process definition from jBPM xml file
        
        else if (actualMimetype.equals(MimetypeMap.MIMETYPE_XML))
        {
            try
            {
                JBPMEngineJpdlXmlReader jpdlReader = new JBPMEngineJpdlXmlReader(definitionStream); 
                ProcessDefinition def = jpdlReader.readProcessDefinition();
                compiledDef = new CompiledProcessDefinition(def, jpdlReader.getProblems());
            }
            catch(Exception e)
            {
                throw new JbpmException("Failed to parse process definition from jBPM xml stream", e);
            }
        }

        return compiledDef;
    }

    /**
     * Gets the Task definition of the specified Task
     * 
     * @param task  the task
     * @return  the task definition
     */
    private TypeDefinition getTaskDefinition(Task task)
    {
        // TODO: Extend jBPM task instance to include dictionary definition qname? 
        QName typeName = QName.createQName(task.getName(), namespaceService);
        TypeDefinition typeDef = dictionaryService.getType(typeName);
        if (typeDef == null)
        {
            typeDef = dictionaryService.getType(WorkflowModel.TYPE_WORKFLOW_TASK);
            if (typeDef == null)
            {
                throw new WorkflowException("Failed to find type definition '" + WorkflowModel.TYPE_WORKFLOW_TASK + "'");
            }
        }
        return typeDef;
    }
    
    /**
     * Convert the specified Type definition to an anonymous Type definition.
     * 
     * This collapses all mandatory aspects into a single Type definition.
     * 
     * @param typeDef  the type definition
     * @return  the anonymous type definition
     */
    private TypeDefinition getAnonymousTaskDefinition(TypeDefinition typeDef)
    {
        List<AspectDefinition> aspects = typeDef.getDefaultAspects();
        List<QName> aspectNames = new ArrayList<QName>(aspects.size());
        for (AspectDefinition aspect : aspects)
        {
            aspectNames.add(aspect.getName());
        }
        return dictionaryService.getAnonymousType(typeDef.getName(), aspectNames);
    }
    
    /**
     * Get JBoss JBPM Id from Engine Global Id
     * 
     * @param id  global id
     * @return  JBoss JBPM Id
     */
    protected long getJbpmId(String id)
    {
        try
        {
            String theLong = createLocalId(id);
            return new Long(theLong);
        }
        catch(NumberFormatException e)
        {
            throw new WorkflowException("Format of id '" + id + "' is invalid", e);
        }
    }

    /**
     * Get the JBoss JBPM Token for the Workflow Path
     * 
     * @param session   JBoss JBPM Graph Session
     * @param pathId  workflow path id
     * @return  JBoss JBPM Token
     */
    protected Token getWorkflowToken(GraphSession session, String pathId)
    {
        // extract process id and token path within process
        String[] path = pathId.split("::");
        if (path.length != 2)
        {
            throw new WorkflowException("Invalid workflow path '" + pathId + "'");
        }

        // retrieve jBPM token for workflow position
        ProcessInstance processInstance = session.loadProcessInstance(getJbpmId(path[0]));
        Token token = processInstance.findToken(path[1]);
        if (token == null)
        {
            throw new WorkflowException("Workflow path '" + pathId + "' does not exist");
        }
        
        return token;
    }

    /**
     * Sets Properties of Task
     * 
     * @param instance  task instance
     * @param properties  properties to set
     */
    @SuppressWarnings("unchecked")
    protected Map<QName, Serializable> getTaskProperties(TaskInstance instance)
    {
        // establish task definition
        TypeDefinition taskDef = getAnonymousTaskDefinition(getTaskDefinition(instance.getTask()));
        Map<QName, AssociationDefinition> taskAssocs = taskDef.getAssociations();

        // map arbitrary task variables
        Map<QName, Serializable> properties = new HashMap<QName, Serializable>(10);
        Map<String, Object> vars = instance.getVariablesLocally();
        for (Entry<String, Object> entry : vars.entrySet())
        {
            String key = entry.getKey();
            Object value = entry.getValue();

            //
            // perform data conversions
            //
            
            // Convert Nodes to NodeRefs
            if (value instanceof org.alfresco.repo.jscript.Node)
            {
                value = ((org.alfresco.repo.jscript.Node)value).getNodeRef();
            }
            
            // Convert Authority name to NodeRefs
            QName qname = QName.createQName(key, this.namespaceService);
            AssociationDefinition assocDef = taskAssocs.get(qname);
            if (assocDef != null && assocDef.getTargetClass().equals(ContentModel.TYPE_PERSON))
            {
                // TODO: Also support group authorities
                if (value instanceof String[])
                {
                    value = mapNameToAuthority((String[])value);
                }
                else if (value instanceof String)
                {
                   value = mapNameToAuthority(new String[] {(String)value});
                }
                else
                {
                    throw new WorkflowException("Task variable '" + qname + "' value is invalid format");
                }
            }    

            // place task variable in map to return
            properties.put(qname, (Serializable)value);
        }

        // map jBPM task instance fields to properties
        properties.put(WorkflowModel.PROP_TASK_ID, instance.getId());
        properties.put(WorkflowModel.PROP_START_DATE, instance.getStart());
        properties.put(WorkflowModel.PROP_DUE_DATE, instance.getDueDate());
        properties.put(WorkflowModel.PROP_COMPLETION_DATE, instance.getEnd());
        properties.put(WorkflowModel.PROP_PRIORITY, instance.getPriority());
        properties.put(ContentModel.PROP_OWNER, instance.getActorId());
        
        // map jBPM task instance collections to associations
        Set pooledActors = instance.getPooledActors();
        if (pooledActors != null)
        {
            String[] pooledActorIds = new String[pooledActors.size()]; 
            pooledActors.toArray(pooledActorIds);
            List<NodeRef> pooledActorNodeRefs = mapNameToAuthority(pooledActorIds);
            properties.put(WorkflowModel.ASSOC_POOLED_ACTORS, (Serializable)pooledActorNodeRefs);
        }

        return properties;
    }
    
    /**
     * Sets Properties of Task
     * 
     * @param instance  task instance
     * @param properties  properties to set
     */
    protected void setTaskProperties(TaskInstance instance, Map<QName, Serializable> properties)
    {
        if (properties == null)
        {
            return;
        }

        // establish task definition
        TypeDefinition taskDef = getAnonymousTaskDefinition(getTaskDefinition(instance.getTask()));
        Map<QName, PropertyDefinition> taskProperties = taskDef.getProperties();
        Map<QName, AssociationDefinition> taskAssocs = taskDef.getAssociations();

        // map each parameter to task
        for (Entry<QName, Serializable> entry : properties.entrySet())
        {
            QName key = entry.getKey();
            Serializable value = entry.getValue();
            
            // determine if writing property
            // NOTE: some properties map to fields on jBPM task instance whilst
            //       others are set in the general variable bag on the task
            PropertyDefinition propDef = taskProperties.get(key);
            if (propDef != null)
            {
                if (propDef.isProtected())
                {
                    // NOTE: only write non-protected properties
                    continue;
                }
                
                // map property to specific jBPM task instance field
                if (key.equals(WorkflowModel.PROP_DUE_DATE))
                {
                    if (!(value instanceof Date))
                    {
                        throw new WorkflowException("Task due date '" + value + "' is invalid");
                    }
                    instance.setDueDate((Date)value);
                    continue;
                }
                else if (key.equals(WorkflowModel.PROP_PRIORITY))
                {
                    if (!(value instanceof Integer))
                    {
                        throw new WorkflowException("Task priority '" + value + "' is invalid");
                    }
                    instance.setPriority((Integer)value);
                    continue;
                }
                else if (key.equals(ContentModel.PROP_OWNER))
                {
                    if (!(value instanceof String))
                    {
                        throw new WorkflowException("Task owner '" + value + "' is invalid");
                    }
                    instance.setActorId((String)value);
                    continue;
                }
            }
            else
            {
                // determine if writing association
                AssociationDefinition assocDef = taskAssocs.get(key);
                if (assocDef != null)
                {
                    // if association is to people, map them to authority names
                    // TODO: support group authorities
                    if (assocDef.getTargetClass().getName().equals(ContentModel.TYPE_PERSON))
                    {
                        String[] authorityNames = mapAuthorityToName((List<NodeRef>)value);
                        if (authorityNames != null && authorityNames.length > 0)
                        {
                            value = (Serializable) (assocDef.isTargetMany() ? authorityNames : authorityNames[0]);
                        }
                    }
                    
                    // map association to specific jBPM task instance field
                    if (key.equals(WorkflowModel.ASSOC_POOLED_ACTORS))
                    {
                        String[] pooledActors = null;
                        if (value instanceof String[])
                        {
                            pooledActors = (String[])value;
                        }
                        else if (value instanceof String)
                        {
                            pooledActors = new String[] {(String)value};
                        }
                        else
                        {
                            throw new WorkflowException("Pooled actors value '" + value + "' is invalid");
                        }
                        instance.setPooledActors(pooledActors);
                        continue;
                    }
                }
            }
            
            // no specific mapping to jBPM task has been established, so place into
            // the generic task variable bag
            String name = key.toPrefixString(this.namespaceService);
            if (value instanceof NodeRef)
            {
                value = new JBPMNode((NodeRef)value, serviceRegistry);
            }
            instance.setVariableLocally(name, value);
        }
    }
    
    
    /**
     * Convert a list of Alfresco Authorities to a list of authority Names
     *  
     * @param authorities  the authorities to convert
     * @return  the authority names
     */
    private String[] mapAuthorityToName(List<NodeRef> authorities)
    {
        String[] names = null;
        if (authorities != null)
        {
            names = new String[authorities.size()];
            int i = 0;
            for (NodeRef person : authorities)
            {
                String name = (String)nodeService.getProperty(person, ContentModel.PROP_USERNAME);
                names[i++] = name;
            }
        }
        return names;
    }
    
    /**
     * Convert a list of authority Names to Alfresco Authorities
     * 
     * @param names  the authority names to convert
     * @return  the Alfresco authorities
     */
    private List<NodeRef> mapNameToAuthority(String[] names)
    {
        List<NodeRef> authorities = null; 
        if (names != null)
        {
            authorities = new ArrayList<NodeRef>(names.length);
            for (String name : names)
            {
                // TODO: Should this be an exception?
                if (personService.personExists(name))
                {
                    authorities.add(personService.getPerson(name));
                }
            }
        }
        return authorities;
    }

    /**
     * Get an I18N Label for a workflow item
     * 
     * @param displayId  message resource id lookup
     * @param labelKey  label to lookup (title or description)
     * @param defaultLabel  default value if not found in message resource bundle
     * @return  the label
     */
    private String getLabel(String displayId, String labelKey, String defaultLabel)
    {
        String key = StringUtils.replace(displayId, ":", "_");
        key += "." + labelKey;
        String label = I18NUtil.getMessage(key);
        return (label == null) ? defaultLabel : label;
    }
    

    //
    // Workflow Data Object Creation...
    //
    
    /**
     * Creates a Workflow Path
     * 
     * @param token  JBoss JBPM Token
     * @return  Workflow Path
     */
    protected WorkflowPath createWorkflowPath(Token token)
    {
        WorkflowPath path = new WorkflowPath();
        path.id = createGlobalId(token.getProcessInstance().getId() + "::" + token.getFullName());
        path.instance = createWorkflowInstance(token.getProcessInstance());
        path.node = createWorkflowNode(token.getNode());
        path.active = !token.hasEnded();
        return path;
    }
    
    /**
     * Creates a Workflow Node
     * 
     * @param node  JBoss JBPM Node
     * @return   Workflow Node
     */
    @SuppressWarnings("unchecked")
    protected WorkflowNode createWorkflowNode(Node node)
    {
        String name = node.getName();
        String processName = node.getProcessDefinition().getName();
        WorkflowNode workflowNode = new WorkflowNode();
        workflowNode.title = getLabel(processName + ".node." + name, TITLE_LABEL, name);
        workflowNode.description = getLabel(processName + ".node." + name, DESC_LABEL, workflowNode.title);
        if (node instanceof HibernateProxy)
        {
            Node realNode = (Node)((HibernateProxy)node).getHibernateLazyInitializer().getImplementation();        
            workflowNode.type = realNode.getClass().getSimpleName();
        }
        else
        {
            workflowNode.type = node.getClass().getSimpleName();
        }
        // TODO: Is there a formal way of determing if task node?
        workflowNode.isTaskNode = workflowNode.type.equals("TaskNode");
        List transitions = node.getLeavingTransitions();
        workflowNode.transitions = new WorkflowTransition[(transitions == null) ? 0 : transitions.size()];
        if (transitions != null)
        {
            int i = 0;
            for (Transition transition : (List<Transition>)transitions)
            {
                workflowNode.transitions[i++] = createWorkflowTransition(transition);
            }
        }
        return workflowNode;
    }
    
    /**
     * Create a Workflow Transition
     * 
     * @param transition  JBoss JBPM Transition
     * @return  Workflow Transition
     */
    protected WorkflowTransition createWorkflowTransition(Transition transition)
    {
        WorkflowTransition workflowTransition = new WorkflowTransition();
        workflowTransition.id = transition.getName();
        Node node = transition.getFrom();
        workflowTransition.isDefault = node.getDefaultLeavingTransition().equals(transition);
        if (workflowTransition.id.length() == 0)
        {
            workflowTransition.title = getLabel(DEFAULT_TRANSITION_LABEL, TITLE_LABEL, workflowTransition.id);
            workflowTransition.description = getLabel(DEFAULT_TRANSITION_LABEL, DESC_LABEL, workflowTransition.title);
        }
        else
        {
            String nodeName = node.getName();
            String processName = node.getProcessDefinition().getName();
            workflowTransition.title = getLabel(processName + ".node." + nodeName + ".transition." + workflowTransition.id, TITLE_LABEL, workflowTransition.id);
            workflowTransition.description = getLabel(processName + ".node." + nodeName + ".transition." + workflowTransition.id, DESC_LABEL, workflowTransition.title);
        }
        return workflowTransition;
    }
        
    /**
     * Creates a Workflow Instance
     * 
     * @param instance  JBoss JBPM Process Instance
     * @return  Workflow instance
     */
    protected WorkflowInstance createWorkflowInstance(ProcessInstance instance)
    {
        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.id = createGlobalId(new Long(instance.getId()).toString());
        workflowInstance.definition = createWorkflowDefinition(instance.getProcessDefinition());
        workflowInstance.active = !instance.hasEnded();
        return workflowInstance;
    }

    /**
     * Creates a Workflow Definition
     * 
     * @param definition  JBoss Process Definition
     * @return  Workflow Definition
     */
    protected WorkflowDefinition createWorkflowDefinition(ProcessDefinition definition)
    {
        WorkflowDefinition workflowDef = new WorkflowDefinition();
        String name = definition.getName();
        workflowDef.title = getLabel(name + ".workflow", TITLE_LABEL, name);
        workflowDef.description = getLabel(name + ".workflow", DESC_LABEL, workflowDef.title);
        workflowDef.id = createGlobalId(new Long(definition.getId()).toString());
        workflowDef.version = new Integer(definition.getVersion()).toString();
        Task startTask = definition.getTaskMgmtDefinition().getStartTask();
        if (startTask != null)
        {
            workflowDef.startTaskDefinition = createWorkflowTaskDefinition(startTask);
        }
        return workflowDef;
    }
    
    /**
     * Creates a Workflow Task
     * 
     * @param task  JBoss Task Instance
     * @return  Workflow Task
     */
    protected WorkflowTask createWorkflowTask(TaskInstance task)
    {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.id = createGlobalId(new Long(task.getId()).toString());
        workflowTask.path = createWorkflowPath(task.getToken());
        workflowTask.state = getWorkflowTaskState(task);
        workflowTask.definition = createWorkflowTaskDefinition(task.getTask());
        workflowTask.properties = getTaskProperties(task);
        String name = task.getName();
        String processName = task.getTask().getProcessDefinition().getName();
        workflowTask.title = getLabel(processName + ".node." + name, TITLE_LABEL, name);
        if (workflowTask.title == null)
        {
            workflowTask.title = workflowTask.definition.metadata.getTitle();
            if (workflowTask.title == null)
            {
                workflowTask.title = name;
            }
        }
        workflowTask.description = getLabel(processName + ".node." + name, DESC_LABEL, workflowTask.title);
        if (workflowTask.description == null)
        {
            String description = workflowTask.definition.metadata.getDescription();
            workflowTask.description = (description == null) ? workflowTask.title : description;
        }
        return workflowTask;
    }
 
    /**
     * Creates a Workflow Task Definition
     * 
     * @param task  JBoss JBPM Task
     * @return  Workflow Task Definition
     */
    protected WorkflowTaskDefinition createWorkflowTaskDefinition(Task task)
    {
        WorkflowTaskDefinition taskDef = new WorkflowTaskDefinition();
        taskDef.id = task.getName();
        taskDef.metadata = getTaskDefinition(task);
        return taskDef;
    }
    
    /**
     * Creates a Workflow Deployment
     * 
     * @param compiledDef  compiled JBPM process definition
     * @return  workflow deployment
     */
    protected WorkflowDeployment createWorkflowDeployment(CompiledProcessDefinition compiledDef)
    {
        WorkflowDeployment deployment = new WorkflowDeployment();
        deployment.definition = createWorkflowDefinition(compiledDef.def);
        deployment.problems = compiledDef.problems;
        return deployment;
    }
    
    /**
     * Get the Workflow Task State for the specified JBoss JBPM Task
     * 
     * @param task  task
     * @return  task state
     */
    protected WorkflowTaskState getWorkflowTaskState(TaskInstance task)
    {
        if (task.hasEnded())
        {
            return WorkflowTaskState.COMPLETED;
        }
        else
        {
            return WorkflowTaskState.IN_PROGRESS;
        }
    }

}
