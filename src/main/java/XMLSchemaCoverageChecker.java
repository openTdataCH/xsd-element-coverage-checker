import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.io.FilenameUtils;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaExternal;
import org.apache.ws.commons.schema.XmlSchemaGroup;
import org.apache.ws.commons.schema.XmlSchemaObject;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;

/**
 * This code allows to check how much of the XSD schema in xsdMain are covered by the example XML files in the xmlMain folder.
 * <p>
 * The xsdMainFileName is the root schema file included in all the examples to use for the coverage check.
 * <p>
 * The outputFile is where the coverage is written into.
 * <p>
 * For verbose output set the print attribute to true.
 * <p>
 * We focus on XML Elements for now, no complex types, groups, etc. We also ignore abstract="true" elements.
 * <p>
 * How it works: 1. we create a large bitmap (elementBitmap): xsdFilePath -> (XSD Element to check ->  Set of XML files having elements matching the XSD element) 2. we iterate through all XML files
 * and compare each element within against the xsd elements and if they match add the file's path to the set 3. we print it all into a xsd file with columns: xsd file; xsd element (N/A if the xsd had
 * no element (e.g., only referecnces)); set of files using the element (N/A if the previous one was N/A)
 */
public final class XMLSchemaCoverageChecker {

    private static XSDSchemaGraph dependencyGraph = new XSDSchemaGraph();

    // Implicit Bitmaps for objects parsed out of the various schema, <file, <element/simpleType/complexType/group, list of usages>>
    private static Map<String, Map<XmlSchemaObject, Set<String>>> elementBitmap = new HashMap<>();
    private static Map<String, Map<XmlSchemaObject, Set<String>>> simpleTypeBitmap = new HashMap<>();

    private static List<String> ignoredFiles = new ArrayList<>();

    private static boolean print = false;

    private XMLSchemaCoverageChecker() {
    }

    public static void main(String[] args) throws URISyntaxException, IOException, ConfigurationException {
        // Extract command line parameters
        int x = 0;
        String xsdMainFileName = "";
        String xsdMain = "";
        String xmlMain = "";
        String outputFilePath = "";
        boolean checkCircularDependency = false;
        while (x < args.length) {
            if (args[x].equals("--help")) {
                System.out.println("XMLSchemaCoverageChecker\n");
                System.out.println("========================\n");
                System.out.println("Checks, which elements (not abstract) in an XSD are not covered by XML examples in a folder\n");
                System.out.println("see also: https://github.com/openTdataCH/xsd-element-coverage-checker\n");
                System.out.println("Parameters:\n");
                System.out.println("--help this help\n");
                System.out.println("--main main xsd file\n");
                System.out.println("--xsd Schemafolder\n");
                System.out.println("--xml XML example folder\n");
                System.out.println("--out output file (CSV)\n");
                System.exit(0);

            } else if (args[x].equals("--main")) {
                if (x + 1 >= args.length) {
                    System.out.println("no main file defined/n");
                    System.exit(1);
                }
                xsdMainFileName = args[x + 1];
                x = x + 1;
            } else if (args[x].equals("--xsd")) {
                if (x + 1 >= args.length) {
                    System.out.println("no xsd directory defined/n");
                    System.exit(1);
                }
                xsdMain = args[x + 1] + "\\";
                x = x + 1;
            } else if (args[x].equals("--xml")) {
                if (x + 1 >= args.length) {
                    System.out.println("no xml example directory defined/n");
                    System.exit(1);
                }
                xmlMain = args[x + 1];
                x = x + 1;
            } else if (args[x].equals("--out")) {
                if (x + 1 >= args.length) {
                    System.out.println("no output file defined/n");
                    System.exit(1);
                }
                outputFilePath = args[x + 1];
                x = x + 1;
            } else {
                // do nothing and go to the next parameter.
            }
            x = x + 1;
        }

        // Load a bitmap of all elements
        if (!"".equals(xsdMain) && !"".equals(xsdMainFileName)) {
            if (checkCircularDependency) {
                System.out.println("Determining circular dependencies");
                buildImportIncludeDependencyGraph(dependencyGraph, null, null, xsdMain, xsdMainFileName);

                if (print) {
                    for (XSDSchemaVertex xsdSchemaVertex : dependencyGraph.getVertices().keySet()) {
                        System.out.println("Vertex: " + xsdSchemaVertex.getName());
                        System.out.println("Ancestors: " + dependencyGraph.getVertices().get(xsdSchemaVertex));
                    }
                }
            }

            System.out.println("Loading the XSDs into memory.");
            loadXsd(xsdMain, xsdMainFileName);
        }

        // Go example by example and check the coverage accross the schema.
        if (!"".equals(xmlMain)) {
            System.out.println("Checking the XMLs against the XSDs.");
            checkXml(xmlMain);
        }

        // Print the results
        if (!"".equals(outputFilePath)) {
            System.out.println("Writing the result to disk");
            printBitmapToCsv(outputFilePath);
        }

        // Print which files were ignored
        if (print) {
            System.out.println("Ignored the following files: " + Arrays.toString(ignoredFiles.toArray()));
        }
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
    private static void buildImportIncludeDependencyGraph(XSDSchemaGraph xsdSchemaGraph, String predecessorCanonicalPath, String predecessorName, String folderName, String fileName)
        throws IOException {
        File rootFile = openFileOrFolder(folderName, fileName);

        List<XmlSchemaObject> schemaItems = getSchemaItems(rootFile, folderName);

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
                    File schemaFile = openFileOrFolder(folderName, schemaLocation);
                    File schemaFolder = openFileOrFolder(schemaFile.getParent(), null);
                    buildImportIncludeDependencyGraph(xsdSchemaGraph, rootFile.getCanonicalPath(), rootFile.getName(), schemaFolder.getCanonicalPath() + File.separator, schemaFile.getName());
                } else {
                    buildImportIncludeDependencyGraph(xsdSchemaGraph, rootFile.getCanonicalPath(), rootFile.getName(), folderName, schemaLocation);
                }
            }
        }
    }

    /**
     * A recursive method to populate the initial bitmaps with the xsd filenames and empty lists of matches.
     *
     * @param folderName the folder to traverse for the schema
     * @param fileName the file to traverse from
     * @throws IOException
     */
    private static void loadXsd(String folderName, String fileName) throws IOException {
        // Get all items
        List<XmlSchemaObject> schemaItems = getSchemaItems(openFileOrFolder(folderName, fileName), folderName);

        // Prepare the mapping on this level
        Map<XmlSchemaObject, Set<String>> elementBitmapFiles = new HashMap<>();
        Map<XmlSchemaObject, Set<String>> simpleTypeBitmapFiles = new HashMap<>();

        for (XmlSchemaObject schemaItem : schemaItems) {
            // For all xml schema elements, do a recursive element resolvance.
            if (schemaItem instanceof XmlSchemaElement) {
                // Ensure it's not an abstract element
                if (!((XmlSchemaElement) schemaItem).isAbstractElement()) {
                    elementBitmapFiles.put(schemaItem, new HashSet<String>());

                    elementBitmap.put(folderName + fileName, elementBitmapFiles);
                }
            } else if (schemaItem instanceof XmlSchemaSimpleType) {
                simpleTypeBitmapFiles.put(schemaItem, new HashSet<String>());

                simpleTypeBitmap.put(folderName + fileName, simpleTypeBitmapFiles);
            } else if (schemaItem instanceof XmlSchemaComplexType) {

            } else if (schemaItem instanceof XmlSchemaGroup) {

            }
            // For all includes and imports call this method recursively, while avoiding circular dependencies
            else if (schemaItem instanceof XmlSchemaExternal) {
                // Get the relative location of the file to be loaded
                String schemaLocation = ((XmlSchemaExternal) schemaItem).getSchemaLocation();

                // Get the associated file
                File schemaFile = openFileOrFolder(folderName, schemaLocation);

                // If we've already been there don't go there again
                if (checkRecursion(schemaFile)) {
                    continue;
                } else {
                    elementBitmap.put(schemaFile.getCanonicalPath(), null);
                    simpleTypeBitmap.put(schemaFile.getCanonicalPath(), null);
                }

                // Differentiate if we have a file in the same or a different folder (indicated by a "/")
                if (schemaLocation.contains("/")) {
                    loadXsd(openFileOrFolder(schemaFile.getParent(), null).getCanonicalPath() + File.separator, schemaFile.getName());
                } else {
                    loadXsd(folderName, schemaLocation);
                }
            } else {
                // TODO check was in here, so we know, what we have to do with it
                System.out.println("ignored element" + schemaItem);
            }
        }
    }

    /**
     * A recursive method to traverse the examples and to add each example to the bitmap that utilized a specific XML element.
     *
     * @param folderName the folder containing the xml files to check
     * @throws IOException
     * @throws ConfigurationException
     */
    private static void checkXml(String folderName) throws IOException, ConfigurationException {
        File xmlFolder = openFileOrFolder(folderName, null);

        for (File fileOrFolder : xmlFolder.listFiles()) {
            if (fileOrFolder.isFile() && "xml".equals(FilenameUtils.getExtension(fileOrFolder.getCanonicalPath()))) {
                // If it's a file let's check it
                if (print) {
                    System.out.println("Checking file: " + fileOrFolder.getCanonicalPath());
                }

                // Alot of code for getting the xml DOM
                Parameters params = new Parameters();
                FileBasedConfigurationBuilder<XMLConfiguration> builder =
                    new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
                        .configure(params.xml().setFile(fileOrFolder));
                XMLConfiguration config = builder.getConfiguration();

                // Now, we get the actual content and check it
                checkElement(fileOrFolder, config.getNodeModel().getRootNode());
            } else if (fileOrFolder.isDirectory()) {
                // If it's a folder let's recurse
                checkXml(fileOrFolder.getCanonicalPath());
            } else {
                ignoredFiles.add(fileOrFolder.getCanonicalPath());
            }
        }
    }

    /**
     * This method recursively checks an element and all its sub-elements for their match with our element bitmap.
     *
     * @param file the example file we're checking, this is mainly needed to reference the xml that covered the given xsd
     * @param rootNode the node to check for existence against the xsd schema
     */
    private static void checkElement(File file, ImmutableNode rootNode) throws IOException {
        // Take the current root node and check it
        String elementName = rootNode.getNodeName();

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : elementBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    if (((XmlSchemaElement) elementEntry.getKey()).getName().contains(elementName)) {
                        elementEntry.getValue().add(file.getCanonicalPath());
                    }
                }
            }
        }

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : simpleTypeBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    if (((XmlSchemaSimpleType) elementEntry.getKey()).getName().contains(elementName)) {
                        elementEntry.getValue().add(file.getCanonicalPath());
                    }
                }
            }
        }

        // Then dip into the child elements
        for (ImmutableNode childNode : rootNode.getChildren()) {
            checkElement(file, childNode);
        }
    }

    /**
     * This code writes the output to the given path: col1: path, col2: schema type, col3: schema name, col4: references
     *
     * @param outputFilePath the path to the output file
     * @throws IOException
     */
    private static void printBitmapToCsv(String outputFilePath) throws IOException {
        FileWriter csvFileWriter = new FileWriter(outputFilePath);

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : elementBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("element").append(File.pathSeparator).append(((XmlSchemaElement) elementEntry.getKey()).getName())
                        .append(File.pathSeparator)
                        .append(elementEntry.getValue().toString())
                        .append(System.lineSeparator());
                }
            } else {
                csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("N/A").append(File.pathSeparator).append("N/A").append(File.pathSeparator)
                    .append("N/A")
                    .append(System.lineSeparator());
            }
        }

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : simpleTypeBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("simpleType").append(File.pathSeparator).append(((XmlSchemaSimpleType) elementEntry.getKey()).getName())
                        .append(File.pathSeparator)
                        .append(elementEntry.getValue().toString())
                        .append(System.lineSeparator());
                }
            } else {
                csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("N/A").append(File.pathSeparator).append("N/A").append(File.pathSeparator)
                    .append("N/A")
                    .append(System.lineSeparator());
            }
        }
    }

    /**
     * This auxiliary method returns the list of xml/xsd schema objects in the given xml/xsd file and folder
     *
     * @param rootFile the file to open and get the schema data out of
     * @param folderName the base folder containing the given xsd/xml file
     * @return (possibly empty) List of XMLSchemaObject
     * @throws FileNotFoundException
     */
    private static List<XmlSchemaObject> getSchemaItems(File rootFile, String folderName) throws FileNotFoundException {
        XmlSchemaCollection schemaCollection = new XmlSchemaCollection();
        schemaCollection.setBaseUri(folderName);
        XmlSchema schema = schemaCollection.read(new StreamSource(new FileInputStream(rootFile)));

        // Get all items
        return schema.getItems();
    }

    /**
     * Auxiliary method to get the xsd/xml file or a subceding folder for the given folder with the given name.
     *
     * @param folderName the base folder containing the given xsd/xml file
     * @param fileName the file name in the folder pointing to the xsd/xml file
     * @return the xsd/xml File
     */
    private static File openFileOrFolder(String folderName, String fileName) {
        File file = null;

        if (fileName != null) {
            file = new File(folderName + fileName);
        } else {
            file = new File(folderName);
        }
        if (!file.exists()) {
            System.out.println("File/Folder does not exist: " + folderName + fileName);
            System.exit(1);
        }
        return file;
    }

    /**
     * This auxiliary method checks if the given file is part of anyone of the bitmaps.
     *
     * @param schemaFile the file to check
     * @return true if any bitmap contains the canonical path of the given file
     * @throws IOException
     */
    private static boolean checkRecursion(File schemaFile) throws IOException {
        Set<String> mergedKeySet = new HashSet<>();

        mergedKeySet.addAll(elementBitmap.keySet());
        mergedKeySet.addAll(simpleTypeBitmap.keySet());

        return mergedKeySet.contains(schemaFile.getCanonicalPath());
    }
}
