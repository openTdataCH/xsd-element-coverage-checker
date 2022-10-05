import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.ws.commons.schema.XmlSchemaExternal;
import org.apache.ws.commons.schema.XmlSchemaObject;

public final class XMLSchemaDependencyChecker {

    private XMLSchemaDependencyChecker() {
    }

    /**
     * This method builds a dependency graph out of the includes and imports in the XSD schema.
     * <p>
     * It also identifies ciruclar dependencies to later allow avoiding to run into infinite loops.
     *
     * @param xsdSchemaGraph A graph of dependencies
     * @param predecessorCanonicalPath On initial call this can be null, but identifies the predecessors of the current file
     * @param predecessorName On initial call this can be null, but identifies the predecessors of the current file
     * @param folderName The current XSD folder to use for dependency detection
     * @param fileName The current "main" XSD file to use for dependency detection
     * @throws IOException
     */
    public static void buildImportIncludeDependencyGraph(XSDSchemaGraph xsdSchemaGraph, String predecessorCanonicalPath, String predecessorName, String folderName, String fileName)
        throws IOException {
        File rootFile = XMLSchemaUtils.openFileOrFolder(folderName, fileName);

        List<XmlSchemaObject> schemaItems = XMLSchemaUtils.getSchemaItems(rootFile, folderName);

        // If the vertex we tried to add to our graph was already in the graph we have a circular dependency and return without going further.
        if (!xsdSchemaGraph.addVertex(predecessorCanonicalPath, predecessorName, rootFile.getCanonicalPath(), rootFile.getName())) {
            return;
        }

        for (XmlSchemaObject schemaItem : schemaItems) {
            // For all includes and imports call this method recursively
            if (schemaItem instanceof XmlSchemaExternal) {  //handles import and includess as the same
                // Get the relative location of the file to be loaded
                String schemaLocation = ((XmlSchemaExternal) schemaItem).getSchemaLocation();

                // Differentiate if we have a file in the same or a different folder
                // A "/" indicates a different folder
                if (schemaLocation.contains("/")) {
                    // Resolve the folder path
                    File schemaFile = XMLSchemaUtils.openFileOrFolder(folderName, schemaLocation);
                    File schemaFolder = XMLSchemaUtils.openFileOrFolder(schemaFile.getParent(), null);
                    buildImportIncludeDependencyGraph(xsdSchemaGraph, rootFile.getCanonicalPath(), rootFile.getName(), schemaFolder.getCanonicalPath() + File.separator, schemaFile.getName());
                } else {
                    buildImportIncludeDependencyGraph(xsdSchemaGraph, rootFile.getCanonicalPath(), rootFile.getName(), folderName, schemaLocation);
                }
            }
        }
    }

}
