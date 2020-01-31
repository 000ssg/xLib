/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.http;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Role-Action-Tag combination used to evaluate access.
 *
 * @author 000ssg
 */
public class RAT implements Serializable, Cloneable {

    private List<String> roles;
    private List<String> actions;
    private List<String> tags;

    public RAT() {
    }

    public RAT(List<String> roles, List<String> actions, List<String> tags) {
        this.roles = roles;
        this.actions = actions;
        this.tags = tags;
    }

    public boolean test(RAT rad) {
        if (rad == null || rad.getRoles() == null && rad.getActions() == null && rad.getTags() == null) {
            return getRoles() == null && getActions() == null && getTags() == null;
        }
        boolean rolesOK = false;
        boolean actionsOK = false;
        boolean tagsOK = false;
        if (getRoles() != null && !roles.isEmpty()) {
            if (rad.getRoles() != null) {
                for (String s : rad.getRoles()) {
                    if (getRoles().contains(s)) {
                        rolesOK = true;
                        break;
                    }
                }
            }
        } else {
            rolesOK = true;
        }
        if (getActions() != null && !actions.isEmpty()) {
            if (rad.getActions() != null) {
                for (String s : rad.getActions()) {
                    if (getActions().contains(s)) {
                        actionsOK = true;
                        break;
                    }
                }
            }
        } else {
            actionsOK = true;
        }
        if (getTags() != null && !tags.isEmpty()) {
            if (rad.getTags() != null) {
                for (String s : rad.getTags()) {
                    if (getTags().contains(s)) {
                        tagsOK = true;
                        break;
                    }
                }
            }
        } else {
            tagsOK = true;
        }
        return rolesOK && actionsOK && tagsOK;
    }

    /**
     * @return the roles
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * @param roles the roles to set
     */
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    /**
     * @return the actions
     */
    public List<String> getActions() {
        return actions;
    }

    /**
     * @param actions the actions to set
     */
    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    /**
     * @return the tags
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * @param tags the tags to set
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public RAT clone() throws CloneNotSupportedException {
        RAT copy = (RAT) super.clone();
        if (copy.actions != null) {
            copy.actions = new ArrayList<String>();
            copy.actions.addAll(actions);
        }
        if (copy.roles != null) {
            copy.roles = new ArrayList<String>();
            copy.roles.addAll(roles);
        }
        if (copy.tags != null) {
            copy.tags = new ArrayList<String>();
            copy.tags.addAll(tags);
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');

        if (roles != null) {
            sb.append("\n  roles=" + roles);
        }
        if (actions != null) {
            sb.append("\n  actions=" + actions);
        }
        if (tags != null) {
            sb.append("\n  tags=" + tags);
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

}
