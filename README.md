# xsd-element-coverage-checker
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
