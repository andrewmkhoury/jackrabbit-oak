/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.jcr;

import org.apache.jackrabbit.oak.api.CoreValue;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Tree.Status;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.util.Function1;
import org.apache.jackrabbit.oak.util.Iterators;

import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.Iterator;
import java.util.List;

public class NodeDelegate extends ItemDelegate {

    private final SessionDelegate sessionDelegate;
    private Tree tree;

    NodeDelegate(SessionDelegate sessionDelegate, Tree tree) {
        this.sessionDelegate = sessionDelegate;
        this.tree = tree;
    }

    NodeDelegate addNode(String relPath) throws RepositoryException {
        Tree parentState = getTree(PathUtils.getParentPath(relPath));
        if (parentState == null) {
            throw new PathNotFoundException(relPath);
        }

        String name = PathUtils.getName(relPath);
        if (parentState.hasChild(name)) {
            throw new ItemExistsException(relPath);
        }

        Tree added = parentState.addChild(name);
        return new NodeDelegate(sessionDelegate, added);
    }

    Iterator<NodeDelegate> getChildren() {
        return nodeDelegateIterator(getTree().getChildren().iterator());
    }

    long getChildrenCount() {
        return getTree().getChildrenCount();
    }

    @Override
    String getName() {
        return getTree().getName();
    }

    Status getNodeStatus() throws InvalidItemStateException {
        return check(getTree().getParent()).getChildStatus(getName());
    }

    NodeDelegate getNodeOrNull(String relOakPath) {
        Tree tree = getTree(relOakPath);
        return tree == null ? null : new NodeDelegate(sessionDelegate, tree);
    }

    NodeDelegate getParent() throws RepositoryException {
        if (check(getTree()).getParent() == null) {
            throw new ItemNotFoundException("Root has no parent");
        }

        return new NodeDelegate(sessionDelegate, getTree().getParent());
    }

    @Override
    String getPath() {
        return '/' + getTree().getPath();
    }

    Iterator<PropertyDelegate> getProperties() throws RepositoryException {
        return propertyDelegateIterator(getTree().getProperties().iterator());
    }

    long getPropertyCount() {
        return getTree().getPropertyCount();
    }

    PropertyDelegate getPropertyOrNull(String relOakPath) {
        Tree parent = getTree(PathUtils.getParentPath(relOakPath));
        if (parent == null) {
            return null;
        }

        String name = PathUtils.getName(relOakPath);
        PropertyState propertyState = parent.getProperty(name);
        return propertyState == null ? null : new PropertyDelegate(
                sessionDelegate, parent, propertyState);
    }

    SessionDelegate getSessionDelegate() {
        return sessionDelegate;
    }

    void remove() {
        getTree().getParent().removeChild(getName());
    }

    PropertyDelegate setProperty(String oakName, CoreValue value) {
        getTree().setProperty(oakName, value);
        return getPropertyOrNull(oakName);
    }

    PropertyDelegate setProperty(String oakName, List<CoreValue> value) {
        getTree().setProperty(oakName, value);
        return getPropertyOrNull(oakName);
    }

    // -----------------------------------------------------------< private >---

    private Tree getTree(String relPath) {
        Tree tree = getTree();
        for (String name : PathUtils.elements(relPath)) {
            if (tree == null) {
                return null;
            }
            tree = tree.getChild(name);
        }
        return tree;
    }

    private static Tree check(Tree t) throws InvalidItemStateException {
        if (t == null) {
            throw new InvalidItemStateException();
        }
        return t;
    }
    
    private synchronized Tree getTree() {
        return tree = sessionDelegate.getTree(tree.getPath());
    }

    private Iterator<NodeDelegate> nodeDelegateIterator(
            Iterator<Tree> childNodeStates) {
        return Iterators.map(childNodeStates,
                new Function1<Tree, NodeDelegate>() {
                    @Override
                    public NodeDelegate apply(Tree state) {
                        return new NodeDelegate(sessionDelegate, state);
                    }
                });
    }

    private Iterator<PropertyDelegate> propertyDelegateIterator(
            Iterator<? extends PropertyState> properties) {
        return Iterators.map(properties,
                new Function1<PropertyState, PropertyDelegate>() {
                    @Override
                    public PropertyDelegate apply(PropertyState propertyState) {
                        return new PropertyDelegate(sessionDelegate, tree,
                                propertyState);
                    }
                });
    }
}
