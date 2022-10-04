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
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroup;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroupMember;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroupRef;
import org.apache.ws.commons.schema.XmlSchemaAttributeOrGroupRef;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaChoiceMember;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaComplexContentExtension;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaContent;
import org.apache.ws.commons.schema.XmlSchemaContentModel;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaExternal;
import org.apache.ws.commons.schema.XmlSchemaGroup;
import org.apache.ws.commons.schema.XmlSchemaGroupParticle;
import org.apache.ws.commons.schema.XmlSchemaGroupRef;
import org.apache.ws.commons.schema.XmlSchemaObject;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaSequenceMember;
import org.apache.ws.commons.schema.XmlSchemaSimpleContentExtension;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;
import org.apache.ws.commons.schema.XmlSchemaType;
import org.apache.ws.commons.schema.utils.XmlSchemaRef;

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
 * <i>We ignore abstract="true" elements.</i>
 * <i>We cannot follow the "base" attribute references of XSD extensions, i.e., the simple or complexTypes they point to. We do however consider the effective extensions, i.e., groups, sequences,
 * attributes defined within. BUT at the moment only the attribute content existed and is considered. We do follow refs to attributegroups.</i>
 * <i>we list XSD-any but do not match it with the XML</i>
 * <i>we do not follow references</i>
 * </li>
 * <p>
 * How it works: 1. we create a large bitmap (elementBitmap): xsdFilePath -> (XSD Element to check ->  Set of XML files having XML-objects matching the XSD-objects) 2. we iterate through all XML files
 * and compare each element within against the xsd-objects and if they match add the file's path to the set 3. we print it all into a xsd file with columns: xsd file; xsd element (N/A if the xsd had
 * no element (e.g., only references)); set of files using the element (N/A if the previous one was N/A)
 */
public final class XMLSchemaCoverageChecker {

    private static XSDSchemaGraph dependencyGraph = new XSDSchemaGraph();

    // Implicit Bitmaps for objects parsed out of the various schema, <file, <element/simpleType/complexType/group, list of usages>>
    private static Map<String, Map<XmlSchemaObject, Set<String>>> elementBitmap = new HashMap<>();
    private static Map<String, Map<XmlSchemaObject, Set<String>>> simpleTypeBitmap = new HashMap<>();
    private static Map<String, Map<XmlSchemaObject, Set<String>>> complexTypeBitmap = new HashMap<>();

    // Implicit Bitmaps for objects parsed out of the various schema, <file, <element/simpleType/complexType/group, list of usages>>
    private static Map<String, Map<String, Set<String>>> allPathsBitmap = new HashMap<>();

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
            loadXsdString(xsdMain, xsdMainFileName); // FIXME: separte handling loadXsd vs loadXsdString
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
     * <p>
     * FIXME this may be obsolete
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
        Map<XmlSchemaObject, Set<String>> complexTypeBitmapFiles = new HashMap<>();

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
                complexTypeBitmapFiles.put(schemaItem, new HashSet<String>());

                complexTypeBitmap.put(folderName + fileName, complexTypeBitmapFiles);
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
                    complexTypeBitmap.put(schemaFile.getCanonicalPath(), null);
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

    private static void loadXsdString(String folderName, String fileName) throws IOException {
        // Get all items
        List<XmlSchemaObject> schemaItems = getSchemaItems(openFileOrFolder(folderName, fileName), folderName);

        // For all xml schema elements, do a recursive (if necessary) element resolvance.
        for (XmlSchemaObject schemaItem : schemaItems) {
            // Handle elements
            if (schemaItem instanceof XmlSchemaElement) {
                // Ensure it's not an abstract element
                if (!((XmlSchemaElement) schemaItem).isAbstractElement()) {
                    handleElement(folderName + fileName, "", (XmlSchemaElement) schemaItem, allPathsBitmap);
                }
            }
            // Handle simpleType
            else if (schemaItem instanceof XmlSchemaSimpleType) {
                handleSimpleType(folderName + fileName, "", (XmlSchemaSimpleType) schemaItem, allPathsBitmap);
            }
            // Handle complexType
            else if (schemaItem instanceof XmlSchemaComplexType) {
                handleComplexType(folderName + fileName, "", (XmlSchemaComplexType) schemaItem, allPathsBitmap);
            }
            // Handle groups
            else if (schemaItem instanceof XmlSchemaGroup) {
                handleGroup(folderName + fileName, "", (XmlSchemaGroup) schemaItem, allPathsBitmap);
            }
            // Handle attributes
            else if (schemaItem instanceof XmlSchemaAttribute) {
                handleAttribute(folderName + fileName, "", (XmlSchemaAttribute) schemaItem, allPathsBitmap);
            }
            // Handle attribute groups
            else if (schemaItem instanceof XmlSchemaAttributeGroup) {
                handleAttributeGroup(folderName + fileName, "", (XmlSchemaAttributeGroup) schemaItem, allPathsBitmap);
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
                    allPathsBitmap.put(schemaFile.getCanonicalPath(), null);
                }

                // Differentiate if we have a file in the same or a different folder (indicated by a "/")
                if (schemaLocation.contains("/")) {
                    loadXsdString(openFileOrFolder(schemaFile.getParent(), null).getCanonicalPath() + File.separator, schemaFile.getName());
                } else {
                    loadXsdString(folderName, schemaLocation);
                }
            } else {
                // TODO check was in here, so we know, what we have to do with it
                System.out.println("loadXsdString: " + " ignored element: " + schemaItem);
            }
        }
    }

    /**
     * Method to handle "element" xsd schema objects.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaElement the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleElement(String filePath, String pathSoFar, XmlSchemaElement schemaElement, Map<String, Map<String, Set<String>>> bitmap) {
        // If we have a name (we do not have ref and may have type).
        if (schemaElement.getName() != null) {
            pathSoFar += "/element/" + schemaElement.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        // If the element contains a ref - follow it
        // when we have ref we do no have name and/or type
        if (schemaElement.isRef()) {
            XmlSchemaRef<XmlSchemaElement> refSchemaElement = schemaElement.getRef();

            handleElement(filePath, pathSoFar, refSchemaElement.getTarget(), bitmap);
        }
        // If the element contains a type - follow it
        // we can have type with or without name
        else if (schemaElement.getSchemaType() != null) {
            XmlSchemaType schemaType = schemaElement.getSchemaType();

            // if the element contains a simpletype
            if (schemaType instanceof XmlSchemaSimpleType) {
                handleSimpleType(filePath, pathSoFar, (XmlSchemaSimpleType) schemaType, bitmap);
            }
            // if the element contains a complextype
            else if (schemaType instanceof XmlSchemaComplexType) {
                handleComplexType(filePath, pathSoFar, (XmlSchemaComplexType) schemaType, bitmap);
            } else {
                System.out.println("handleElement: " + "Did not handle schema type: " + schemaType.getName());
            }
        }
        // Else log what we've missed
        else {
            System.out.println("handleElement: " + "Did not handle schema element: " + schemaElement.getName());
        }
    }

    /**
     * Method to handle "simpleType" xsd schema objects.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaSimpleType the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleSimpleType(String filePath, String pathSoFar, XmlSchemaSimpleType schemaSimpleType, Map<String, Map<String, Set<String>>> bitmap) {
        // Add simpleType by name to path
        if (schemaSimpleType.getName() != null) {
            pathSoFar += "/simpleType/" + schemaSimpleType.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        // then we're done, because simpletype is a leaf
    }

    /**
     * Method to handle "complexType" xsd schema objects.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaComplexType the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleComplexType(String filePath, String pathSoFar, XmlSchemaComplexType schemaComplexType, Map<String, Map<String, Set<String>>> bitmap) {
        // Add complexType by name to path
        if (schemaComplexType.getName() != null) {
            pathSoFar += "/complexType/" + schemaComplexType.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        XmlSchemaContentModel schemaContentModel = schemaComplexType.getContentModel();

        // Handle contents, i.e., we have to handle extensions, but can ignore restrictions
        if (schemaContentModel != null) {
            XmlSchemaContent schemaContent = schemaContentModel.getContent();

            // handle simplecontent
            if (schemaContent instanceof XmlSchemaSimpleContentExtension) {
                handleExtension(filePath, pathSoFar, schemaContent, bitmap);
            }
            // handle complexcontent
            else if (schemaContent instanceof XmlSchemaComplexContentExtension) {
                handleExtension(filePath, pathSoFar, schemaContent, bitmap);
            } else {
                System.out.println("loadComplexTypeRecursively: " + "unhandled schemaContent: " + schemaContent);
            }
        }
        // If no content check for the mutually exclusive: group, all, choice, sequence
        else if (schemaComplexType.getParticle() != null) {
            // handle sequence
            if (schemaComplexType.getParticle() instanceof XmlSchemaSequence) {
                handleSequence(filePath, pathSoFar, (XmlSchemaSequence) schemaComplexType.getParticle(), bitmap);
            }
            // handle choice
            else if (schemaComplexType.getParticle() instanceof XmlSchemaChoice) {
                handleChoice(filePath, pathSoFar, (XmlSchemaChoice) schemaComplexType.getParticle(), bitmap);
            }
            // handle groupref
            else if (schemaComplexType.getParticle() instanceof XmlSchemaGroupRef) {
                handleGroupRef(filePath, pathSoFar, (XmlSchemaGroupRef) schemaComplexType.getParticle(), bitmap);
            } else {
                System.out.println("loadComplexTypeRecursively: " + "unhandled particle: " + schemaComplexType.getParticle());
            }
        }
        // handle attributes
        else if (schemaComplexType.getAttributes() != null && !schemaComplexType.getAttributes().isEmpty()) {
            handleAttributes(filePath, pathSoFar, schemaComplexType.getAttributes(), bitmap);
        } else {
            System.out.println("loadComplexTypeRecursively: " + "unhandled complexType: " + schemaComplexType);
        }
    }

    /**
     * Method to handle "group" xsd schema objects, beneath the root schema element (otherwise references are used).
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaGroup the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleGroup(String filePath, String pathSoFar, XmlSchemaGroup schemaGroup, Map<String, Map<String, Set<String>>> bitmap) {
        if (schemaGroup.getName() != null) {
            // Add group by name to path
            pathSoFar += "/group/" + schemaGroup.getName();

            // Update the bitmap
            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        // handle particle
        handleGroupParticle(filePath, pathSoFar, schemaGroup.getParticle(), bitmap);
    }

    /**
     * Method to handle "attribute" xsd schema objects. Specifically these are bound within the schem contents.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaAttribute the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleAttribute(String filePath, String pathSoFar, XmlSchemaAttribute schemaAttribute, Map<String, Map<String, Set<String>>> bitmap) {
        // If we have a name (we do not have ref and may have type).
        if (schemaAttribute.getName() != null) {
            pathSoFar += "/attribute/" + schemaAttribute.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        // If the attributes contains a ref - follow it
        // when we have ref we do no have name and/or type
        if (schemaAttribute.isRef()) {
            XmlSchemaRef<XmlSchemaAttribute> schemaAttributeRef = schemaAttribute.getRef();

            handleAttribute(filePath, pathSoFar, schemaAttributeRef.getTarget(), bitmap);
        }
        // If the attribute contains a type - follow it
        // we can have type with or without name
        else if (schemaAttribute.getSchemaType() != null) {
            XmlSchemaType schemaType = schemaAttribute.getSchemaType();

            // if the element contains a simpletype, which should be the only valid entry
            if (schemaType instanceof XmlSchemaSimpleType) {
                handleSimpleType(filePath, pathSoFar, (XmlSchemaSimpleType) schemaType, bitmap);
            } else {
                System.out.println("handleAttribute: " + "Did not handle schema type: " + schemaType.getName());
            }
        } else if (schemaAttribute.getName() != null) {
            // Skip this we've handled it above, but need it here to avoid double consideration.
        }
        // Else log what we've missed
        else {
            System.out.println("handleAttribute: " + "Did not handle schema attribute: " + schemaAttribute.getName());
        }
    }

    /**
     * Method to handle "attributeGroup" xsd schema objects.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaAttributeGroup the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleAttributeGroup(String filePath, String pathSoFar, XmlSchemaAttributeGroup schemaAttributeGroup, Map<String, Map<String, Set<String>>> bitmap) {
        // If we have a name (we do not have ref and may have type).
        if (schemaAttributeGroup.getName() != null) {
            pathSoFar += "/attributeGroup/" + schemaAttributeGroup.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        for (XmlSchemaAttributeGroupMember schemaAttributeGroupMember : schemaAttributeGroup.getAttributes()) {
            if (schemaAttributeGroupMember instanceof XmlSchemaAttribute) {
                handleAttribute(filePath, pathSoFar, (XmlSchemaAttribute) schemaAttributeGroupMember, bitmap);
            } else if (schemaAttributeGroup instanceof XmlSchemaAttributeGroup) {
                handleAttributeGroup(filePath, pathSoFar, (XmlSchemaAttributeGroup) schemaAttributeGroupMember, bitmap);
            } else {
                System.out.println("handleAttributeGroup: " + "Did not handle schema attribute group member: " + schemaAttributeGroupMember);
            }
        }
    }

    /**
     * Method to handle "extension" xsd schema objects. Specifically these are bound within the schem contents.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaContent the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleExtension(String filePath, String pathSoFar, XmlSchemaContent schemaContent, Map<String, Map<String, Set<String>>> bitmap) {
        pathSoFar += "/extension";

        // Get the extension's name
        if (schemaContent instanceof XmlSchemaSimpleContentExtension) {
            // Extend by the base path
            XmlSchemaSimpleContentExtension schemaSimpleContentExtension = ((XmlSchemaSimpleContentExtension) schemaContent);
            pathSoFar += "/" + schemaSimpleContentExtension.getBaseTypeName().getLocalPart();
            addPathToBitmap(filePath, pathSoFar, bitmap);
            // handle attributes
            handleAttributes(filePath, pathSoFar, schemaSimpleContentExtension.getAttributes(), bitmap);
        } else if (schemaContent instanceof XmlSchemaComplexContentExtension) {
            // Extend by the base path
            XmlSchemaComplexContentExtension schemaComplexContentExtension = ((XmlSchemaComplexContentExtension) schemaContent);
            pathSoFar += "/" + schemaComplexContentExtension.getBaseTypeName().getLocalPart();
            addPathToBitmap(filePath, pathSoFar, bitmap);
            // handle attributes
            handleAttributes(filePath, pathSoFar, schemaComplexContentExtension.getAttributes(), bitmap);
        } else {
            System.out.println("handleExtensions: " + "unhandled: " + schemaContent);
        }
    }

    /**
     * Method to handle "sequence" xsd schema objects
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaSequence the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleSequence(String filePath, String pathSoFar, XmlSchemaSequence schemaSequence, Map<String, Map<String, Set<String>>> bitmap) {
        pathSoFar += "/sequence";

        for (XmlSchemaSequenceMember schemaSequenceMember : schemaSequence.getItems()) {
            // handle any
            if (schemaSequenceMember instanceof XmlSchemaAny) {
                handleAny(filePath, pathSoFar, (XmlSchemaAny) schemaSequenceMember, bitmap);
            }
            // handle choice
            else if (schemaSequenceMember instanceof XmlSchemaChoice) {
                handleChoice(filePath, pathSoFar, (XmlSchemaChoice) schemaSequenceMember, bitmap);
            }
            // handle groupref
            else if (schemaSequenceMember instanceof XmlSchemaGroupRef) {
                handleGroupRef(filePath, pathSoFar, (XmlSchemaGroupRef) schemaSequenceMember, bitmap);
            }
            // handle element
            else if (schemaSequenceMember instanceof XmlSchemaElement) {
                handleElement(filePath, pathSoFar, (XmlSchemaElement) schemaSequenceMember, bitmap);
            }
            // handle sequence
            else if (schemaSequenceMember instanceof XmlSchemaSequence) {
                handleSequence(filePath, pathSoFar, (XmlSchemaSequence) schemaSequenceMember, bitmap);
            } else {
                System.out.println("loadComplexTypeRecursively: " + "unhandled sequence member: " + schemaSequenceMember);
            }
        }
    }

    /**
     * Method to handle "any" xsd schema objects
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaAny the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleAny(String filePath, String pathSoFar, XmlSchemaAny schemaAny, Map<String, Map<String, Set<String>>> bitmap) {
        addPathToBitmap(filePath, pathSoFar, bitmap);
    }

    /**
     * Method to handle "choice" xsd schema objects
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaChoice the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleChoice(String filePath, String pathSoFar, XmlSchemaChoice schemaChoice, Map<String, Map<String, Set<String>>> bitmap) {
        pathSoFar += "/choice";

        for (XmlSchemaChoiceMember schemaChoiceMember : schemaChoice.getItems()) {
            // handle any
            if (schemaChoiceMember instanceof XmlSchemaAny) {
                handleAny(filePath, pathSoFar, (XmlSchemaAny) schemaChoiceMember, bitmap);
            }
            // handle choice
            else if (schemaChoiceMember instanceof XmlSchemaChoice) {
                handleChoice(filePath, pathSoFar, (XmlSchemaChoice) schemaChoiceMember, bitmap);
            }
            // handle groupref
            else if (schemaChoiceMember instanceof XmlSchemaGroupRef) {
                handleGroupRef(filePath, pathSoFar, (XmlSchemaGroupRef) schemaChoiceMember, bitmap);
            }
            // handle elment
            else if (schemaChoiceMember instanceof XmlSchemaElement) {
                handleElement(filePath, pathSoFar, (XmlSchemaElement) schemaChoiceMember, bitmap);
            }
            // handle sequence
            else if (schemaChoiceMember instanceof XmlSchemaSequence) {
                handleSequence(filePath, pathSoFar, (XmlSchemaSequence) schemaChoiceMember, bitmap);
            } else {
                System.out.println("handleChoice: " + "unhandled choice member: " + schemaChoiceMember);
            }
        }
    }

    /**
     * Method to handle "groupref" xsd schema objects (when groups are used outside below a schema element.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaGroupRef the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleGroupRef(String filePath, String pathSoFar, XmlSchemaGroupRef schemaGroupRef, Map<String, Map<String, Set<String>>> bitmap) {
        if (schemaGroupRef.getRefName() != null) {
            // Add group by name to path
            pathSoFar += "/groupref/" + schemaGroupRef.getRefName().getLocalPart();

            // Update the bitmap
            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        handleGroupParticle(filePath, pathSoFar, schemaGroupRef.getParticle(), bitmap);
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
        elementName = elementName.substring(elementName.lastIndexOf(":") + 1);

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : elementBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    String elementEntryName = ((XmlSchemaElement) elementEntry.getKey()).getName();
                    elementEntryName = elementEntryName.substring(elementEntryName.lastIndexOf(":") + 1);
                    if (elementName.equals(elementEntryName)) {
                        elementEntry.getValue().add(file.getCanonicalPath());
                    }
                }
            }
        }

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : simpleTypeBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    String elementEntryName = ((XmlSchemaSimpleType) elementEntry.getKey()).getName();
                    elementEntryName = elementEntryName.substring(elementEntryName.lastIndexOf(":") + 1);
                    if (elementName.equals(elementEntryName)) {
                        elementEntry.getValue().add(file.getCanonicalPath());
                    }
                }
            }
        }

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : complexTypeBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    String elementEntryName = ((XmlSchemaComplexType) elementEntry.getKey()).getName();
                    elementEntryName = elementEntryName.substring(elementEntryName.lastIndexOf(":") + 1);
                    if (elementName.equals(elementEntryName)) {
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

        for (Entry<String, Map<XmlSchemaObject, Set<String>>> entry : complexTypeBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaObject, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("complexType").append(File.pathSeparator)
                        .append(((XmlSchemaComplexType) elementEntry.getKey()).getName())
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
     * Auxiliary method used to handle the list of attributes and/or attributegroup refs. The latter exists usually when the attribute group is not beneath the schema root element.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param attributes the list of attributes or attributegroup refs
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleAttributes(String filePath, String pathSoFar, List<XmlSchemaAttributeOrGroupRef> attributes, Map<String, Map<String, Set<String>>> bitmap) {
        if (attributes != null && !attributes.isEmpty()) {
            for (XmlSchemaAttributeOrGroupRef schemaAttributeOrGroupRef : attributes) {
                if (schemaAttributeOrGroupRef instanceof XmlSchemaAttribute) {
                    handleAttribute(filePath, pathSoFar, (XmlSchemaAttribute) schemaAttributeOrGroupRef, bitmap);
                } else if (schemaAttributeOrGroupRef instanceof XmlSchemaAttributeGroupRef) {
                    XmlSchemaAttributeGroupRef schemaAttributeGroupRef = (XmlSchemaAttributeGroupRef) schemaAttributeOrGroupRef;

                    if (schemaAttributeGroupRef.getRef() != null && schemaAttributeGroupRef.getRef().getTarget() != null) {
                        handleAttributeGroup(filePath, pathSoFar, schemaAttributeGroupRef.getRef().getTarget(), bitmap);
                    }
                } else {
                    System.out.println("handleAttributes: " + "unhandled: " + schemaAttributeOrGroupRef);
                }
            }
        }
    }

    /**
     * Auxiliary method used to handle the elements of groups and group references.
     *
     * @param filePath the path to the file containing schema object
     * @param pathSoFar the path leading to the schema object
     * @param schemaGroupParticle the schema object to process
     * @param bitmap the bitmap to enhance with the information
     */
    private static void handleGroupParticle(String filePath, String pathSoFar, XmlSchemaGroupParticle schemaGroupParticle, Map<String, Map<String, Set<String>>> bitmap) {
        if (schemaGroupParticle != null) {
            // handle sequence
            if (schemaGroupParticle instanceof XmlSchemaSequence) {
                handleSequence(filePath, pathSoFar, (XmlSchemaSequence) schemaGroupParticle, bitmap);
            }
            // handle choice
            else if (schemaGroupParticle instanceof XmlSchemaChoice) {
                handleChoice(filePath, pathSoFar, (XmlSchemaChoice) schemaGroupParticle, bitmap);
            } else {
                System.out.println("handleGroupParticle: " + "unhandled particle: " + schemaGroupParticle);
            }
        }
    }

    /**
     * Auxiliary method to extend a given bitmap with the pathSoFar for the filePath
     *
     * @param filePath the path to the file to extend with the given xsd-path to later extend
     * @param pathSoFar the xsd-path to add for the given file
     * @param bitmap the bitmap to extend
     */
    private static void addPathToBitmap(String filePath, String pathSoFar, Map<String, Map<String, Set<String>>> bitmap) {
        if (bitmap.get(filePath) == null) {
            Map<String, Set<String>> pathToFilesBitmap = new HashMap<>();
            pathToFilesBitmap.put(pathSoFar, new HashSet<>());
            bitmap.put(filePath, pathToFilesBitmap);
        } else {
            bitmap.get(filePath).put(pathSoFar, new HashSet<>());
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
        mergedKeySet.addAll(complexTypeBitmap.keySet());

        return mergedKeySet.contains(schemaFile.getCanonicalPath());
    }
}
