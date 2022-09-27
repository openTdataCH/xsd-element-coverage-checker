import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XSDSchemaGraph {

    private Map<XSDSchemaVertex, Set<XSDSchemaVertex>> vertices = new HashMap<>();

    /**
     * @param predecessorUniqueId
     * @param uniqueId
     * @return true if uniqueId was new else false; error if predecessor is not real, excep that first add is OK with predecessor NULL!
     */
    public boolean addVertex(String predecessorUniqueId, String predecessorName, String uniqueId, String name) {
        XSDSchemaVertex potentiallyNewVertex = new XSDSchemaVertex(uniqueId, name);
        XSDSchemaVertex predecessorVertex = new XSDSchemaVertex(predecessorUniqueId, predecessorName);

        // If mapping is empty this is the first vertex
        if (vertices.size() == 0) {
            vertices.put(potentiallyNewVertex, new HashSet<>());

            return true;
        } else {
            // If mapping not empty we should have a valid predecessorVertex or else we'll stop this mess
            if (predecessorVertex == null || !vertices.containsKey(predecessorVertex)) {
                System.err.println("The predecessorVertex: " + predecessorUniqueId + " was not found.");
                System.exit(1);
            } else {
                // If the predecessor is valid we either have a new vertex or a recursion
                if (!vertices.containsKey(potentiallyNewVertex)) {
                    // Add the vertex and let the predecessor point to it
                    vertices.put(potentiallyNewVertex, new HashSet<>());
                    vertices.get(predecessorVertex).add(potentiallyNewVertex);

                    return true;
                } else {
                    // Do not (cannot) add the vertex, but let the predecessor point to it
                    // Mark the vertex as being a circular dependency if it is one or just stop
                    // traversing
                    vertices.get(predecessorVertex).add(potentiallyNewVertex);

                    Set<XSDSchemaVertex> predecessors = new HashSet<>();
                    predecessors.add(predecessorVertex);
                    boolean circularDependency = checkCircularDependency(predecessors, potentiallyNewVertex);
                    potentiallyNewVertex.setCircularDependency(circularDependency);

                    return false;
                }
            }
        }

        return false;
    }

    private boolean checkCircularDependency(Set<XSDSchemaVertex> predecessors, XSDSchemaVertex existingVertex) {
        boolean circularDependency = false;

        if (predecessors.size() > 0) {
            if (predecessors.contains(existingVertex)) {
                return true;
            } else {
                Set<XSDSchemaVertex> grandparents = new HashSet<>();

                for (XSDSchemaVertex predecessor : predecessors) {
                    for (XSDSchemaVertex vertex : this.vertices.keySet()) {
                        XSDSchemaVertex vertexSuccessor = getXSDSchemaVertexFromSet(predecessor, this.vertices.get(vertex));

                        if (vertexSuccessor != null) {
                            if (!vertexSuccessor.isCircularDependency()) {
                                // We have a circular dependency that we found out only now
                                if (existingVertex.equals(vertex)) {
                                    vertexSuccessor.setCircularDependency(true);
                                }

                                grandparents.add(vertex);
                            }
                        }
                    }
                }

                circularDependency |= checkCircularDependency(grandparents, existingVertex);
            }
        }

        return circularDependency;
    }

    private XSDSchemaVertex getXSDSchemaVertexFromSet(XSDSchemaVertex object, Set<XSDSchemaVertex> set) {
        for (XSDSchemaVertex setObject : set) {
            if (object.equals(setObject)) {
                return setObject;
            }
        }

        return null;
    }

    private Set<XSDSchemaVertex> cloneSet(Set<XSDSchemaVertex> toClone) {
        HashSet<XSDSchemaVertex> clone = new HashSet<>();

        clone.addAll(toClone);

        return clone;
    }

    public Map<XSDSchemaVertex, Set<XSDSchemaVertex>> getVertices() {
        return vertices;
    }
}
