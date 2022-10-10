import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.io.FilenameUtils;

/**
 * This code allows to check how much of the XSD schema in xsdMain are covered by the example XML files in the xmlMain folder.
 * <p>
 * The xsdMainFileName is the root schema file included in all the examples to use for the coverage check.
 * <p>
 * The outputFile is where the coverage is written into.
 * <p>
 * For verbose output set the print attribute to true.
 * <p>
 * what we do not include:
 * <li>
 * <i>We cannot follow the "base" attribute references of XSD extensions, i.e., the simple or complexTypes they point to. We do however consider the effective extensions, i.e., groups, sequences,
 * attributes defined within. BUT at the moment only the attribute content existed and is considered. We do follow refs to attributegroups.</i>
 * <i>we list XSD-any but do not match it with the XML</i>
 * <i>we resolve group references, but not wrappers who are not renamed properly, e.g., if a group named "ServiceFacilityGroup" references a group named "siriServiceFacilityGroup"</i>
 * </li>
 * <p>
 * How it works: 1. we create a large bitmap (elementBitmap): xsdFilePath -> (XSD Element to check ->  Set of XML files having XML-objects matching the XSD-objects) 2. we iterate through all XML files
 * and compare each element within against the xsd-objects and if they match add the file's path to the set 3. we print it all into a xsd file with columns: xsd file; xsd element (N/A if the xsd had
 * no element (e.g., only references)); set of files using the element (N/A if the previous one was N/A)
 */
public final class XMLSchemaCoverageChecker {

    private static XSDSchemaGraph dependencyGraph = new XSDSchemaGraph();

    // Implicit Bitmaps for objects parsed out of the various schema, <file, <element/simpleType/complexType/group, list of usages>>
    private static Map<String, Map<String, Set<String>>> allPathsBitmap = new HashMap<>();

    private static List<String> ignoredFiles = new ArrayList<>();
	
	public static boolean verbose=false;


    //private static boolean print = false;

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
			if (args[x].equals("--verbose")){
				verbose=true;
				x=x+1;
			} else if (args[x].equals("--help")) {
                System.out.println("XMLSchemaCoverageChecker\n");
                System.out.println("========================\n");
                System.out.println("Checks, which objects in an XSD are not covered by XML examples in a folder\n");
                System.out.println("see also: https://github.com/openTdataCH/xsd-element-coverage-checker\n");
                System.out.println("Parameters:\n");
                System.out.println("--help this help\n");
                System.out.println("--main main xsd file\n");
                System.out.println("--xsd Schemafolder\n");
                System.out.println("--xml XML example folder\n");
                System.out.println("--out output file (CSV)\n");
				System.out.println("--verbose writes more output to standard output during processing\n");
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
                XMLSchemaDependencyChecker.buildImportIncludeDependencyGraph(dependencyGraph, null, null, xsdMain, xsdMainFileName);

                if (verbose) {
                    for (XSDSchemaVertex xsdSchemaVertex : dependencyGraph.getVertices().keySet()) {
                        System.out.println("Vertex: " + xsdSchemaVertex.getName());
                        System.out.println("Ancestors: " + dependencyGraph.getVertices().get(xsdSchemaVertex));
                    }
                }
            }

            System.out.println("Loading the XSDs into memory.");
            allPathsBitmap = XMLSchemaBitmapBuilder.loadXsdString(xsdMain, xsdMainFileName, allPathsBitmap); // FIXME: separte handling loadXsd vs loadXsdString
        }

        // Go example by example and check the coverage accross the schema.
        if (!"".equals(xmlMain)) {
            System.out.println("Checking the XMLs against the XSDs.");
            checkXml(xmlMain);
        }

        // Print the results
        if (!"".equals(outputFilePath)) {
            System.out.println("Writing the result to disk");
            XMLSchemaUtils.printBitmapToCsv(outputFilePath, allPathsBitmap);
        }

        // Print which files were ignored
        if (verbose) {
            System.out.println("Ignored the following files: " + Arrays.toString(ignoredFiles.toArray()));
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
        File xmlFolder = XMLSchemaUtils.openFileOrFolder(folderName, null);

        for (File fileOrFolder : xmlFolder.listFiles()) {
            if (fileOrFolder.isFile() && "xml".equals(FilenameUtils.getExtension(fileOrFolder.getCanonicalPath()))) {
                // If it's a file let's check it
                if (verbose) {
                    System.out.println("Checking file: " + fileOrFolder.getCanonicalPath());
                }

                // Alot of code for getting the xml DOM
                Parameters params = new Parameters();
                FileBasedConfigurationBuilder<XMLConfiguration> builder =
                    new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
                        .configure(params.xml().setFile(fileOrFolder));
                XMLConfiguration config = builder.getConfiguration();

                // Now, we get the actual content and check it
                checkElement(fileOrFolder, config.getNodeModel().getRootNode(), "");
            } else if (fileOrFolder.isDirectory()) {
                // If it's a folder let's recurse
                checkXml(fileOrFolder.getCanonicalPath());
            } else {
                ignoredFiles.add(fileOrFolder.getCanonicalPath());
            }
        }
    }

    /**
     * This method recursively checks any xml object and all its sub-objects for their match with our bitmap.
     *
     * @param file the example file we're checking, this is mainly needed to reference the xml that covered the given xsd
     * @param rootNode the node to check for existence against the xsd schema
     */
    private static void checkElement(File file, ImmutableNode rootNode, String path) throws IOException {
        // Take the current root node and check it
        String rootNodeNodeName = rootNode.getNodeName();
        rootNodeNodeName = rootNodeNodeName.substring(rootNodeNodeName.lastIndexOf(":") + 1);
        String rootNodePath = path + "/" + rootNodeNodeName;

        for (Entry<String, Map<String, Set<String>>> filePathToStructure : allPathsBitmap.entrySet()) {
            if (filePathToStructure.getValue() != null) {
                for (Entry<String, Set<String>> structurePathToOccurence : filePathToStructure.getValue().entrySet()) {
                    String structurePath = structurePathToOccurence.getKey().substring(structurePathToOccurence.getKey().lastIndexOf(":") + 1);
                    if (XMLSchemaUtils.fullSubpath(rootNodePath, structurePath, "/")) {
                        structurePathToOccurence.getValue().add(file.getCanonicalPath());
                    }
                }
            }
        }

        // Then dip into the child elements
        for (ImmutableNode childNode : rootNode.getChildren()) {
            checkElement(file, childNode, rootNodePath);
        }
    }
}
