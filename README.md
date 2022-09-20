# xsd-element-coverage-checker
## HowTo run
1. goto XMLSchemaCoverageChecker.java and change the paths there, particularly, the "..."
2. open command prompt
3. goto folder "xsd-element-coverage-checker" or whatever you called it
4. type gradlew.bat build
5. type gradlew.bat run
   1. If this does not work check the stacktrace with gradlew.bat run --stacktrace

## More Info
**Important:** for NeTEx which is the reference use case we had the code runs for 30-40 minutes.

Java code to check how many elements of an XSD-namespace are covered by one or many XML example files

It's a gradle project that contains only one runnable .java file

As per javadoc:
This code allows to check how much of the XSD schema in xsdMain are covered by the example XML files in the xmlMain folder.
<p>
We focus on XML Elements for now, no complex types, groups, etc. We also ignore abstract="true" elements.
<p>
How it works:

1. we create a large bitmap (elementBitmap): xsdFilePath -> (XSD Element to check ->  Set of XML files having elements matching the XSD element) 
2. we iterate through all XML files and compare each element within against the xsd elements and if they match add the file's path to the set 
3. we print it all into a xsd file with columns: xsd file; xsd element (N/A if the xsd had no element (e.g., only referecnces)); set of files using the element (N/A if the previous one was N/A)
