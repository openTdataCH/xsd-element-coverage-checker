import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class XSDSchemaTree {

    private XSDSchemaNode root;

    public XSDSchemaTree() {
        root = new XSDSchemaNode();
    }

    public XSDSchemaTree(String rootName, String rootFullName, String rootCanonicalPath) {
        root = new XSDSchemaNode();
        root.setName(rootName);
        root.setFullName(rootFullName);
        root.setCanonicalPath(rootCanonicalPath);
    }

    public class XSDSchemaNode {

        private String name;
        private String fullName;
        private String canonicalPath;
        private List<XSDSchemaNode> children = new ArrayList<>();
        private XSDSchemaNode parent;

        public XSDSchemaNode() {
        }

        public XSDSchemaNode(XSDSchemaNode parent) {
            this.parent = parent;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getCanonicalPath() {
            return canonicalPath;
        }

        public void setCanonicalPath(String canonicalPath) {
            this.canonicalPath = canonicalPath;
        }

        public List<XSDSchemaNode> getChildren() {
            return children;
        }

        public void setChildren(List<XSDSchemaNode> children) {
            this.children = children;
        }

        public XSDSchemaNode getParent() {
            return this.parent;
        }

        public void setParent(XSDSchemaNode parent) {
            this.parent = parent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            XSDSchemaNode that = (XSDSchemaNode) o;
            return Objects.equals(name, that.name) && Objects.equals(fullName, that.fullName) && Objects.equals(canonicalPath, that.canonicalPath) && children.equals(
                that.children) && Objects.equals(parent, that.parent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, fullName, canonicalPath, children, parent);
        }

        @Override
        public String toString() {
            return "XSDSchemaNode{" +
                "canonicalPath='" + canonicalPath + '\'' +
                '}';
        }
    }
}