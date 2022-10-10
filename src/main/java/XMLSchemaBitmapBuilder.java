import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroup;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroupMember;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroupRef;
import org.apache.ws.commons.schema.XmlSchemaAttributeOrGroupRef;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaChoiceMember;
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

public final class XMLSchemaBitmapBuilder {

    private static String groupRef = "groupRef";
    private static String delimiter = "/";

    private static int substitutionRounds = 3;
    private static Map<String, Set<String>> substitutionGroups = new HashMap<>();

    private static Set<String> ignoredNamespaces = new HashSet<>(Arrays.asList("http://www.w3.org/XML/1998/namespace"));

    private XMLSchemaBitmapBuilder() {
    }

    public static Map<String, Map<String, Set<String>>> loadXsdString(String folderName, String fileName, Map<String, Map<String, Set<String>>> bitmap) throws IOException {
        // In a first round we build the bitmap
        loadXsdStringBase(folderName, fileName, bitmap);

        // To avoid concurrent access issues we create a deep copy of the bitmap
        Map<String, Map<String, Set<String>>> bitmapDeepCopy = XMLSchemaUtils.createBitmapDeepCopy(bitmap);

        // FIXME this is a workaround to handle the deficiency of the XMLSchema library who can identify a group ref but not resolve the group
        // In a second round we resolve the referenced groups to their actual instances
        return resolveGroupRefs(bitmap, bitmapDeepCopy, folderName + fileName);
    }

    /**
     * This method has two purposes: 1) It resolves all "/groupRef/" instances to the actual group and its sub-elements, i.e., it replaces those elements in the bitmap deep copy 2) It removes the
     * group names, which were only left in the paths to allow the substitution step in 1). 3) after step 1) is repeated -substitutionRounds- number of times we handle substitutionGroups 4) when the
     * substitutiongroups have been handled we truncate if wanted (TODO: DISCUSS remove all remaining paths containing a groupRef?)
     *
     * @param bitmap the original bitmap for which we substitute the group refs
     * @param bitmapDeepCopy a deep copy of the bitmap within which we do the de-facto substitutions
     * @param rootFile the root xsd file if not null we consider this a truncate wish and throw out all other mappings
     * @return the substituted bitmap
     */
    public static Map<String, Map<String, Set<String>>> resolveGroupRefs(Map<String, Map<String, Set<String>>> bitmap, Map<String, Map<String, Set<String>>> bitmapDeepCopy, String rootFile) {
        int progress = 0;
        for (int i = substitutionRounds; i > 0; i--) {
            progress = 0;
            for (String filePath : bitmap.keySet()) {
                System.out.print(
                    "Substituting groupRefs, round " + (substitutionRounds + 1 - i) + " progress: " + Math.round(100f * (float) ((float) (++progress) / (float) bitmap.keySet().size())) + "% \r");
                if (bitmap.get(filePath) != null) {
                    for (String xsdPath : bitmap.get(filePath).keySet()) {
                        String[] pathSegments = xsdPath.split(groupRef);

                        // If the path has no group ref we don't need to do anything
                        if (pathSegments.length > 1) {
                            // If the ref is at the root level we remove it.
                            if (delimiter.equals(pathSegments[0])) {
                                bitmapDeepCopy.get(filePath).remove(xsdPath);
                                continue;
                            }
                            // We do not care for wrappers who do not rename their reference!
                            if (pathSegments[0].replaceAll(delimiter, "").equals(pathSegments[1].replaceAll(delimiter, ""))) {
                                System.out.println("resolveGroupRefs" + " ignoring wrappers who do not rename their reference: " + xsdPath);
                                bitmapDeepCopy.get(filePath).remove(xsdPath);
                                addPathToBitmap(filePath, pathSegments[0], bitmapDeepCopy);
                                continue;
                            }
                            if (pathSegments.length > 2) {
                                System.out.println("resolveGroupRefs" + " more than 2 path segments: " + pathSegments);
                            }

                            // We then search through the bitmap deep copy for the given segment.
                            // The paths matching the given one are then stored into a list, because we need to replace them
                            // we also remove the group name from the paths.
                            List<String> groupPaths = gatherGroupPaths(bitmap, pathSegments[1], true);

                            // We now replace the given path with those gathered from the reference.
                            // For that we remove the given/old path and then add the new/resolved ones.
                            bitmapDeepCopy.get(filePath).remove(xsdPath);
                            for (String groupPath : groupPaths) {
                                addPathToBitmap(filePath, pathSegments[0] + groupPath.replaceFirst(delimiter, ""), bitmapDeepCopy);
                            }
                        }
                    }
                }
            }

            // Swap the bitmap with the deep copy for the next round
            bitmap = bitmapDeepCopy;
            bitmapDeepCopy = XMLSchemaUtils.createBitmapDeepCopy(bitmap);
        }

        // Handle the substitutionGroups, but first update the bitmap
        bitmap = bitmapDeepCopy;
        bitmapDeepCopy = XMLSchemaUtils.createBitmapDeepCopy(bitmap);
        progress = 0;
        for (String filePath : bitmap.keySet()) {
            System.out.print("Resolving substitutionGroups, progress: " + Math.round((100f * (float) ((float) (++progress) / (float) bitmap.keySet().size()))) + "% \r");
            if (bitmap.get(filePath) != null) {
                for (String xsdPath : bitmap.get(filePath).keySet()) {
                    // Check if the given path contains any of the groups to substitute
                    for (String substitutionGroup : substitutionGroups.keySet()) {
                        if (!xsdPath.endsWith(delimiter)) {
                            xsdPath += delimiter;
                        }
                        String[] pathSegments = xsdPath.split(substitutionGroup);

                        // If we could split there was a containment, but we only want to replace the group as a "root"
                        // For example we replace /OJP/OJPResponse/AbstractDiscoveryDelivery but not /OJP/OJPResponse/AbstractDiscoveryDelivery/ErrorCondition
                        if (pathSegments.length > 1 && pathSegments[1].replaceAll(delimiter, "").isBlank()) {
                            // Then go through the substitutions for the given group
                            for (String substitution : substitutionGroups.get(substitutionGroup)) {
                                // Gather all paths of the substitution
                                // we do not remove the substitution from the paths
                                List<String> groupPaths = gatherGroupPaths(bitmap, delimiter + substitution, false);

                                // Then we expand the deep copy mapping
                                for (String groupPath : groupPaths) {
                                    addPathToBitmap(filePath, pathSegments[0] + groupPath.replaceFirst(delimiter, ""), bitmapDeepCopy);
                                }
                            }
                        }
                    }
                }
            }
        }

        // if root file exists truncate the bitmap
        if (rootFile != null) {
            Map<String, Map<String, Set<String>>> finalMap = new HashMap<>();
            finalMap.put(rootFile, bitmapDeepCopy.get(rootFile));
            return finalMap;
        }

        return bitmapDeepCopy;
    }

    /**
     * This auxiliary method allows gathering the xsdPaths containing the given subPath and if wanted to replace the subPath in the returned list of paths.
     *
     * @param bitmap the bitmap to get the list of XSD paths from
     * @param subPath the subPath to find in the bitmap
     * @param removeSubPath whether the result should containt the subpath itself.
     * @return list of paths in the bitmap containing the subpath (with or without the subpath itself)
     */
    private static List<String> gatherGroupPaths(Map<String, Map<String, Set<String>>> bitmap, String subPath, boolean removeSubPath) {
        List<String> groupPaths = new ArrayList<>();

        for (String filePathSubstitute : bitmap.keySet()) {
            if (bitmap.get(filePathSubstitute) != null) {
                for (String xsdPathSubstitute : bitmap.get(filePathSubstitute).keySet()) {
                    if (XMLSchemaUtils.fullSubpath(xsdPathSubstitute, subPath, delimiter)) {
                        // only include those group substitutes that also start with the group name
                        if (!xsdPathSubstitute.startsWith(subPath)) {
                            continue;
                        }

                        // remove the actual group name when adding substitutes
                        if (removeSubPath) {
                            xsdPathSubstitute = xsdPathSubstitute.replace(subPath, "");
                        }

                        if (!xsdPathSubstitute.isBlank()) {
                            groupPaths.add(xsdPathSubstitute);
                        }
                    }
                }
            }
        }

        return groupPaths;
    }

    private static void loadXsdStringBase(String folderName, String fileName, Map<String, Map<String, Set<String>>> bitmap) throws IOException {
        // Get all items
        List<XmlSchemaObject> schemaItems = XMLSchemaUtils.getSchemaItems(XMLSchemaUtils.openFileOrFolder(folderName, fileName), folderName);

        // For all xml schema elements, do a recursive (if necessary) element resolvance.
        for (XmlSchemaObject schemaItem : schemaItems) {
            // Handle elements
            if (schemaItem instanceof XmlSchemaElement) {
                handleElement(folderName + fileName, "", (XmlSchemaElement) schemaItem, bitmap);
            }
            // Handle simpleType
            else if (schemaItem instanceof XmlSchemaSimpleType) {
                handleSimpleType(folderName + fileName, "", (XmlSchemaSimpleType) schemaItem, bitmap);
            }
            // Handle complexType
            else if (schemaItem instanceof XmlSchemaComplexType) {
                handleComplexType(folderName + fileName, "", (XmlSchemaComplexType) schemaItem, bitmap);
            }
            // Handle groups
            else if (schemaItem instanceof XmlSchemaGroup) {
                handleGroup(folderName + fileName, "", (XmlSchemaGroup) schemaItem, bitmap);
            }
            // Handle attributes
            else if (schemaItem instanceof XmlSchemaAttribute) {
                handleAttribute(folderName + fileName, "", (XmlSchemaAttribute) schemaItem, bitmap);
            }
            // Handle attribute groups
            else if (schemaItem instanceof XmlSchemaAttributeGroup) {
                handleAttributeGroup(folderName + fileName, "", (XmlSchemaAttributeGroup) schemaItem, bitmap);
            }
            // For all includes and imports call this method recursively, while avoiding circular dependencies
            else if (schemaItem instanceof XmlSchemaExternal) {
                // Get the relative location of the file to be loaded
                String schemaLocation = ((XmlSchemaExternal) schemaItem).getSchemaLocation();

                // Get the associated file
                File schemaFile = XMLSchemaUtils.openFileOrFolder(folderName, schemaLocation);

                // If we've already been there don't go there again
                if (bitmap.keySet().contains(schemaFile.getCanonicalPath())) {
                    continue;
                } else {
                    bitmap.put(schemaFile.getCanonicalPath(), null);
                }

                // Differentiate if we have a file in the same or a different folder (indicated by a delimiter)
                if (schemaLocation.contains(delimiter)) {
                    loadXsdStringBase(XMLSchemaUtils.openFileOrFolder(schemaFile.getParent(), null).getCanonicalPath() + File.separator, schemaFile.getName(), bitmap);
                } else {
                    loadXsdStringBase(folderName, schemaLocation, bitmap);
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
            pathSoFar += delimiter + schemaElement.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);

            // Check if we have a substitutiton group and store it in our map
            if (schemaElement.getSubstitutionGroup() != null) {
                if (substitutionGroups.get(schemaElement.getSubstitutionGroup().getLocalPart()) == null) {
                    substitutionGroups.put(schemaElement.getSubstitutionGroup().getLocalPart(), new HashSet<>());
                }

                substitutionGroups.get(schemaElement.getSubstitutionGroup().getLocalPart()).add(schemaElement.getName());
            }
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
        // We do not need to add simpleType name to path
        /* FIXME if needed
        if (schemaSimpleType.getName() != null) {
            pathSoFar += delimiter + schemaSimpleType.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);
        }
         */

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
        // We do not need to add simpleType name to path
        /* FIXME if needed
        // Add complexType by name to path
        if (schemaComplexType.getName() != null) {
            pathSoFar += delimiter + schemaComplexType.getName();

            addPathToBitmap(filePath, pathSoFar, bitmap);
        }
         */

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
            pathSoFar += delimiter + schemaGroup.getName();

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
            // FIXME we knowingly ignore all native types
            if (!ignoredNamespaces.contains(schemaAttribute.getQName().getNamespaceURI())) {
                pathSoFar += delimiter + schemaAttribute.getName();

                addPathToBitmap(filePath, pathSoFar, bitmap);
            }
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
            pathSoFar += delimiter + schemaAttributeGroup.getName();

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
        /*
        // FIXME if needed
        String extensionBaseName = "";
        if (schemaContent instanceof XmlSchemaSimpleContentExtension) {
            extensionBaseName = ((XmlSchemaSimpleContentExtension) schemaContent).getBaseTypeName().getLocalPart();
        } else if (schemaContent instanceof XmlSchemaComplexContentExtension) {
            extensionBaseName = ((XmlSchemaComplexContentExtension) schemaContent).getBaseTypeName().getLocalPart();
        } else {
            System.out.println("handleExtensions: " + "unhandled schemaContent instance: " + schemaContent);
        }
        if(!"".equals(extensionBaseName)) {
            pathSoFar += delimiter + extensionBaseName
            addPathToBitmap(filePath, pathSoFar, bitmap);
        }
         */

        // For simple content we only need to handle the attributes
        if (schemaContent instanceof XmlSchemaSimpleContentExtension) {
            handleAttributes(filePath, pathSoFar, ((XmlSchemaSimpleContentExtension) schemaContent).getAttributes(), bitmap);
        }
        // For complex content we handle the attributes and potentially the group ref, sequence, and choice
        else if (schemaContent instanceof XmlSchemaComplexContentExtension) {
            XmlSchemaComplexContentExtension schemaComplexContentExtension = ((XmlSchemaComplexContentExtension) schemaContent);

            handleAttributes(filePath, pathSoFar, schemaComplexContentExtension.getAttributes(), bitmap);

            if (schemaComplexContentExtension.getParticle() != null) {
                // handle sequence
                if (schemaComplexContentExtension.getParticle() instanceof XmlSchemaSequence) {
                    handleSequence(filePath, pathSoFar, (XmlSchemaSequence) schemaComplexContentExtension.getParticle(), bitmap);
                }
                // handle choice
                else if (schemaComplexContentExtension.getParticle() instanceof XmlSchemaChoice) {
                    handleChoice(filePath, pathSoFar, (XmlSchemaChoice) schemaComplexContentExtension.getParticle(), bitmap);
                }
                // handle groupref
                else if (schemaComplexContentExtension.getParticle() instanceof XmlSchemaGroupRef) {
                    handleGroupRef(filePath, pathSoFar, (XmlSchemaGroupRef) schemaComplexContentExtension.getParticle(), bitmap);
                } else {
                    System.out.println("handleExtension: " + "unhandled particle: " + schemaComplexContentExtension.getParticle());
                }
            }
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
        // These references need to later be replaced by the actual instances of the groups
        if (schemaGroupRef.getRefName() != null) {
            // Add group by name to path
            pathSoFar += delimiter + groupRef + delimiter + schemaGroupRef.getRefName().getLocalPart();

            // Update the bitmap
            addPathToBitmap(filePath, pathSoFar, bitmap);
        }

        handleGroupParticle(filePath, pathSoFar, schemaGroupRef.getParticle(), bitmap);
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
}
