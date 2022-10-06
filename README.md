# xsd-element-coverage-checker

For the object defined in a root Schema (and all included and imported schemas - using a given
schema folder) the program checks if they are covered by examples in an example folder. This is a
sort of XSD-coverage test.

The program can write the result to a file. The coverage is not only indicated by a boolean but
includes a set of all example-files that cover a particular schema object.

We cover almost all XSD-constellations. Here's an image of the XSD hierarchy covered so far:
![image of the XSD schema tree](/xsd_tree_coverage.png "The XSD schema tree and which parts are covered by the code")

## How to run it?

1. open command prompt
2. goto folder "xsd-element-coverage-checker" or whatever you called it
3. type gradlew.bat build
4. type gradlew run --args="--main ojp.xsd --xsd D:\development\OJP-changes_for_v1.1\ --xml D:
   \development\OJP-changes_for_v1.1\examples\ --out ./ojp.csv  (change the perameters)

## Parameters

* --help this help
* --main main xsd file
* --xsd Schemafolder
* --xml XML example folder
* --out output file (CSV)

## How the program works

How it works:

1. we create a large bitmap: xsdFilePath -> (XSD object to check ->  Set of XML
   files having elements matching the XSD element)
2. we iterate through all XML files and compare each element within against the xsd elements and if
   they match add the file's path to the set
3. we print it all into a xsd file with columns: xsd file; xsd object type; xsd object (N/A if the
   xsd had no object definition (e.g., only referecnces)); set of files using the element (N/A if
   the previous one was N/A)

## Notes

* The runtime may be significent. Around 4 minutes for OJP.
* We ignore elements with abstract="true"
* You may run out of memory, make sure to allocate enough using -Xmx javac command
* We cannot follow the "base" attribute references of XSD extensions, i.e., the simple or
  complexTypes they point to. We do however consider the effective extensions, i.e., groups,
  sequences, etc.
* We list XSD-any but do not match it with the XML
* We resolve group references, but not wrappers who are not renamed properly, e.g., if a group
  named "ServiceFacilityGroup" references a group named "siriServiceFacilityGroup", it's out
* Important: Please be aware that the output is quite verbose, we've built in a logic that truncates
  the results to the main XSD file only. For example if OJP.xsd is the main schema we build up its
  XSD-tree and then only match that against the XML. You can change this in the
  XMLSchemaBitmapBuilder, by not passing a filePath to the resolveGroupRefs method.
* Despite the above limitation the output remains verbose, we leave it to the users to finally
  decide what part of the output is relevant. For example OJP.xsd includes siri schemata which are
  then replaced with substitutionGroups, thus, it is likely that siri schemata can be neglected in
  the coverage analysis. However, we do not assume this pre-hoc but provide you with both outputs.
* The recursion depth, i.e., how far we resolve group references is defined by the
  substitutionRounds parameter in the XMLSchemaBitmapBuilder file. For example for OJP this needs to
  be 3. We did not resolve infinitely as that may not be wanted for a given analysis, but if you
  wish you can be certain it is infinite if you set INT_MAX. Note, the more recursions you have the
  higher is the memory consumption.

## Issues

https://github.com/openTdataCH/xsd-element-coverage-checker\n

## Impressum

David Rudi, Matthias Günter
SBB

## License

MIT-License

## TODOs

* ~~Does it sensibly work for complexTypes and groups !!~~
* ~~Test it with OJP~~
* ~~Testing that it works for imports~~
* ~~Does foundAMatch work as advertised~~
* ~~Ignoring folders and files with a paremeter~~
* ~~Make sure namespaces are handled correctly~~
* Error handling
* Improve in program documentation
* Validate against more examples