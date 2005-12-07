/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.state;

import org.apache.jackrabbit.core.ItemId;
import org.apache.jackrabbit.core.WorkspaceImpl;
import org.apache.jackrabbit.core.TransactionException;
import org.apache.log4j.Logger;

import javax.jcr.ReferentialIntegrityException;

/**
 * Extension to <code>LocalItemStateManager</code> that remembers changes on
 * multiple save() requests and commits them only when an associated transaction
 * is itself committed.
 */
public class TransactionalItemStateManager extends LocalItemStateManager {

    /**
     * Logger instance.
     */
    private static Logger log = Logger.getLogger(TransactionalItemStateManager.class);

    /**
     * ThreadLocal that holds the ChangeLog while this state manager is in one
     * of the {@link #prepare()}, {@link #commit()}, {@link #rollback()}
     * methods.
     */
    private static ThreadLocal commitLog = new ThreadLocal() {
        protected synchronized Object initialValue() {
            return new CommitLog();
        }
    };

    /**
     * Current instance-local change log.
     */
    private transient ChangeLog txLog;

    /**
     * Creates a new <code>LocalItemStateManager</code> instance.
     *
     * @param sharedStateMgr shared state manager
     */
    public TransactionalItemStateManager(SharedItemStateManager sharedStateMgr,
                                         WorkspaceImpl wspImpl) {
        super(sharedStateMgr, wspImpl);
    }

    /**
     * Set transactional change log to use.
     * @param txLog change log, may be <code>null</code>.
     * @param threadLocal if <code>true</code> set thread-local change log;
     *                    otherwise set instance-local change log
     */
    public void setChangeLog(ChangeLog txLog, boolean threadLocal) {
        if (threadLocal) {
            ((CommitLog) commitLog.get()).setChanges(txLog);
        } else {
            this.txLog = txLog;
        }
    }

    /**
     * Prepare a transaction.
     * @throws TransactionException if an error occurs
     */
    public void prepare() throws TransactionException {
        ChangeLog txLog = ((CommitLog) commitLog.get()).getChanges();
        if (txLog != null) {
            try {
                sharedStateMgr.checkReferentialIntegrity(txLog);
            } catch (ReferentialIntegrityException rie) {
                log.error(rie);
                txLog.undo(sharedStateMgr);
                throw new TransactionException("Unable to prepare transaction.", rie);
            } catch (ItemStateException ise) {
                log.error(ise);
                txLog.undo(sharedStateMgr);
                throw new TransactionException("Unable to prepare transaction.", ise);
            }
        }
    }

    /**
     * Commit changes made within a transaction
     * @throws TransactionException if an error occurs
     */
    public void commit() throws TransactionException {
        ChangeLog txLog = ((CommitLog) commitLog.get()).getChanges();
        if (txLog != null) {
            try {
                super.update(txLog);
            } catch (ReferentialIntegrityException rie) {
                log.error(rie);
                txLog.undo(sharedStateMgr);
                throw new TransactionException("Unable to commit transaction.", rie);
            } catch (ItemStateException ise) {
                log.error(ise);
                txLog.undo(sharedStateMgr);
                throw new TransactionException("Unable to commit transaction.", ise);
            }
            txLog.reset();
        }
    }

    /**
     * Rollback changes made within a transaction
     */
    public void rollback() {
        ChangeLog txLog = ((CommitLog) commitLog.get()).getChanges();
        if (txLog != null) {
            txLog.undo(sharedStateMgr);
        }
    }

    //-----------------------------------------------------< ItemStateManager >
    /**
     * {@inheritDoc}
     * <p/>
     * If this state manager is committing changes, this method first checks
     * the commitLog ThreadLocal. Else if associated to a transaction check
     * the transactional change log. Fallback is always the call to the base
     * class.
     */
    public ItemState getItemState(ItemId id)
            throws NoSuchItemStateException, ItemStateException {

        ChangeLog changeLog = ((CommitLog) commitLog.get()).getChanges();
        if (changeLog != null) {
            // check items in commit log
            ItemState state = changeLog.get(id);
            if (state != null) {
                return state;
            }
        } else if (txLog != null) {
            // check items in change log
            ItemState state = txLog.get(id);
            if (state != null) {
                return state;
            }
        }
        return super.getItemState(id);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * If this state manager is committing changes, this method first checks
     * the commitLog ThreadLocal. Else if associated to a transaction check
     * the transactional change log. Fallback is always the call to the base
     * class.
     */
    public boolean hasItemState(ItemId id) {
        ChangeLog changeLog = ((CommitLog) commitLog.get()).getChanges();
        if (changeLog != null) {
            // check items in commit log
            try {
                ItemState state = changeLog.get(id);
                if (state != null) {
                    return true;
                }
            } catch (NoSuchItemStateException e) {
                return false;
            }
        } else if (txLog != null) {
            // check items in change log
            try {
                ItemState state = txLog.get(id);
                if (state != null) {
                    return true;
                }
            } catch (NoSuchItemStateException e) {
                return false;
            }
        }
        return super.hasItemState(id);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * If this state manager is committing changes, this method first
     * checks the commitLog ThreadLocal. Else if associated to a transaction
     * check the transactional change log. Fallback is always the call to
     * the base class.
     */
    public NodeReferences getNodeReferences(NodeReferencesId id)
            throws NoSuchItemStateException, ItemStateException {

        ChangeLog changeLog = ((CommitLog) commitLog.get()).getChanges();
        if (changeLog != null) {
            // check commit log
            NodeReferences refs = changeLog.get(id);
            if (refs != null) {
                return refs;
            }
        } else if (txLog != null) {
            // check change log
            NodeReferences refs = txLog.get(id);
            if (refs != null) {
                return refs;
            }
        }
        return super.getNodeReferences(id);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * If this state manager is committing changes, this method first
     * checks the commitLog ThreadLocal. Else if associated to a transaction
     * check the transactional change log. Fallback is always the call to
     * the base class.
     */
    public boolean hasNodeReferences(NodeReferencesId id) {
        ChangeLog changeLog = ((CommitLog) commitLog.get()).getChanges();
        if (changeLog != null) {
            // check commit log
            if (changeLog.get(id) != null) {
                return true;
            }
        } else if (txLog != null) {
            // check change log
            if (txLog.get(id) != null) {
                return true;
            }
        }
        return super.hasNodeReferences(id);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * If associated with a transaction, simply merge the changes given to
     * the ones already known (removing items that were first added and
     * then again deleted).
     */
    protected void update(ChangeLog changeLog)
            throws ReferentialIntegrityException, StaleItemStateException,
            ItemStateException {
        if (txLog != null) {
            txLog.merge(changeLog);
        } else {
            super.update(changeLog);
        }
    }

    //--------------------------< inner classes >-------------------------------

    /**
     * Helper class that serves as a container for a ChangeLog in a ThreadLocal.
     * The <code>CommitLog</code> is associated with a <code>ChangeLog</code>
     * while the <code>TransactionalItemStateManager</code> is in the commit
     * method.
     */
    private static class CommitLog {

        /**
         * The changes that are about to be committed
         */
        private ChangeLog changes;

        /**
         * Sets changes that are about to be committed.
         *
         * @param changes that are about to be committed, or <code>null</code>
         *                if changes have been committed and the commit log should be reset.
         */
        private void setChanges(ChangeLog changes) {
            this.changes = changes;
        }

        /**
         * The changes that are about to be committed, or <code>null</code> if
         * the <code>TransactionalItemStateManager</code> is currently not
         * committing any changes.
         *
         * @return the changes about to be committed.
         */
        private ChangeLog getChanges() {
            return changes;
        }
    }
}
