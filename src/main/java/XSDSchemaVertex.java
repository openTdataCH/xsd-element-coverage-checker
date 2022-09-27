import java.util.Objects;

public class XSDSchemaVertex {

    /**
     * A human-readable unique identification of the vertex, e.g., the canonicalpath to a XSD
     */
    private String uniqueId;

    /**
     * A short name of the vertex that can be used in toString()
     */
    private String name;

    private boolean isCircularDependency = false;

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public boolean isCircularDependency() {
        return isCircularDependency;
    }

    public void setCircularDependency(boolean circularDependency) {
        isCircularDependency = circularDependency;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public XSDSchemaVertex(String uniqueId, String name) {
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public XSDSchemaVertex(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        XSDSchemaVertex that = (XSDSchemaVertex) o;
        return Objects.equals(uniqueId, that.uniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueId);
    }

    @Override
    public String toString() {
        return "XSDSchemaVertex{" +
            "name='" + name + '\'' +
            ", isCircularDependency=" + isCircularDependency +
            '}';
    }
}
