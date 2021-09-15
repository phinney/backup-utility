/*
 * Copyright 2021 PhinneyRidge.com
 *        Licensed under the Apache License, Version 2.0 (the "License");
 *        you may not use this file except in compliance with the License.
 *        You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *        Unless required by applicable law or agreed to in writing, software
 *        distributed under the License is distributed on an "AS IS" BASIS,
 *        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *        See the License for the specific language governing permissions and
 *        limitations under the License.
 */
package com.phinneyridge.tools.backup;

import java.util.List;

public interface FileSelection {
    /**
     * get the list of selected Paths.  Paths are the first lower level item in the
     * tree that is checked. Although child elements may be checked, they are not
     * included in the list
     * @return the list of selected paths
     */
    List<String> getPaths();

    /**
     * set the tree nodes from the given paths
     * @param paths list of path to select in the tree
     * @return true if all the paths where found and selected, false if at least one path could not
     * be found in the tree and hence not set.  Those paths found will be set, and those not found
     * are simple ignored.  It usually means that a selected file path was deleted, and that can happen.
     */
    boolean setTreeNodePaths(List<String> paths);

    /**
     * A test to determine if any nodes in the tree are selected
     * @return true if any node in the tree is selected, else false
     */
    boolean areAnyNodesSelected();

    /**
     * clears all nodes in the tree that have been selected
     */
    void clearAllSelectedNodes();
}
