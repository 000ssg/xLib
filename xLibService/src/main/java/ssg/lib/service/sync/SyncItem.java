/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
package ssg.lib.service.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author sesidoro
 */
public class SyncItem {

    public static enum SyncType {
        domain,
        group,
        item
    }

    public static enum SyncUpdate {
        invalid,
        same,
        in,
        out,
        corrupted
    }

    private SyncType type;
    private String id;
    private long created;
    private Long timestamp;
    List<SyncItem> children;
    private Object value;

    //
    private SyncItem parent;

    public SyncItem() {
    }

    public SyncItem(
            SyncType type,
            String id,
            Long timestamp,
            Object value
    ) {
        this.type = type;
        this.id = id;
        this.timestamp = timestamp;
        this.value = value;
        this.created = timestamp;
    }

    public SyncItem(
            SyncType type,
            String id,
            Long timestamp
    ) {
        this.type = type;
        this.id = id;
        this.timestamp = timestamp;
        this.created = timestamp;
    }

    public SyncItem(
            SyncType type,
            String id
    ) {
        this.type = type;
        this.id = id;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the timestamp
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
        if (timestamp != null && parent != null) {
            if (parent.getTimestamp() == null || parent.getTimestamp() < timestamp) {
                parent.setTimestamp(timestamp);
            }
        }
    }

    /**
     * @return the type
     */
    public SyncType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(SyncType type) {
        this.type = type;
    }

    public List<SyncItem> children() {
        return children;
    }

    /**
     * @return the value
     */
    public Object getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(Object value) {
        this.value = value;
        touch();
    }

    @Override
    public String toString() {
        return "SyncItem{"
                + "type=" + type
                + ", id=" + id
                + (parent != null ? ", parent=" + parent.getId() : "")
                + ", created=" + created
                + ", timestamp=" + timestamp
                + (children != null ? ", children=" + children.size() : "")
                + (value != null ? ", value=" + value : "")
                + '}';
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(this);
        if (children() != null) {
            for (SyncItem ci : children()) {
                sb.append("\n  " + ci.dump().replace("\n", "\n  "));
            }
        }
        return sb.toString();
    }

    public String path() {
        return parent != null ? parent.path() + "/" + getId() : getId();
    }

    public SyncItem find(String id) {
        if (id == null) {
            return null;
        }
        if (id.equals(getId())) {
            return this;
        }
        if (children() != null) {
            for (SyncItem ci : children()) {
                SyncItem ri = ci.find(id);
                if (ri != null) {
                    return ri;
                }
            }
        }
        return null;
    }

    public void touch() {
        setTimestamp(System.currentTimeMillis());
    }

    public SyncUpdate add(SyncItem item) {
        if (item == null) {
            return SyncUpdate.invalid;
        }
        if (children == null) {
            children = new ArrayList<>();
        }
        if (!children.contains(item)) {
            children.add(item);
            item.parent = this;
            if (item.getTimestamp() != null && (getTimestamp() == null || item.getTimestamp() > getTimestamp())) {
                setTimestamp(item.getTimestamp());
            }
            return SyncUpdate.in;
        } else {
            SyncItem own = children.get(children.indexOf(item));
            return update(own, item);
        }
    }

    public SyncUpdate remove(SyncItem item) {
        if (children == null || item == null || item.getId() == null) {
            return SyncUpdate.invalid;
        }
        int idx = children.indexOf(item);
        if (idx == -1) {
            return SyncUpdate.invalid;
        }
        onRemoved(children.remove(idx));
        return SyncUpdate.in;
    }

    public SyncUpdate update(SyncItem own, SyncItem external) {
        if (own == null
                || external == null
                || !own.getType().equals(external.getType())
                || own.getId() == null
                || !own.getId().equals(external.getId())) {
            return SyncUpdate.invalid;
        }
        if (own.getTimestamp() == null) {
            if (external.getTimestamp() == null) {
                return SyncUpdate.corrupted;
            }
            own.setValue(external.getValue());
            own.setTimestamp(external.getTimestamp());
            return SyncUpdate.in;
        } else {
            if (external.getTimestamp() == null) {
                return SyncUpdate.invalid;
            }
            if (own.getTimestamp() < external.getTimestamp()) {
                own.setValue(external.getValue());
                return SyncUpdate.in;
            } else if (own.getTimestamp() > external.getTimestamp()) {
                external.setValue(own.getValue());
                return SyncUpdate.out;
            } else {
                if (own.getValue() == null) {
                    if (external.getValue() != null) {
                        return SyncUpdate.corrupted;
                    }
                } else if (!own.getValue().equals(external.getValue())) {
                    return SyncUpdate.corrupted;
                }
            }
        }
        return SyncUpdate.same;
    }
    
    public void onRemoved(SyncItem child) {
        touch();
        if(parent!=null) parent.onRemoved(child);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.type);
        hash = 89 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SyncItem other = (SyncItem) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        return true;
    }

    /**
     * @return the created
     */
    public long getCreated() {
        return created;
    }

    /**
     * @param created the created to set
     */
    public void setCreated(long created) {
        this.created = created;
    }

}
