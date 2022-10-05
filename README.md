# xsd-element-coverage-checker
The program can check, if the elements defined in an Schema is covered by examples in an example folder. This is a sort of coverage test.


## How to run it?
1. open command prompt
2. goto folder "xsd-element-coverage-checker" or whatever you called it
3. type gradlew.bat build
4. type gradlew run --args="--main ojp.xsd --xsd D:\development\OJP-changes_for_v1.1\ --xml D:\development\OJP-changes_for_v1.1\examples\ --out ./ojp.csv  (change the perameters)


## Parameters
* --help this help 
* --main main xsd file 
* --xsd Schemafolder
* --xml XML example folder 
* --out output file (CSV) 

## Notes
* The runtime may be significent.
* Java code to check how many elements of an XSD-namespace are covered by one or many XML example files
* We focus on XML elements for now, no complex types, groups, etc 
* We ignore elements with abstract="true"
* You may run out of memory, make sure to allocate enough


## How the program works

How it works:
1. we create a large bitmap (elementBitmap): xsdFilePath -> (XSD Element to check ->  Set of XML files having elements matching the XSD element) 
2. we iterate through all XML files and compare each element within against the xsd elements and if they match add the file's path to the set 
3. we print it all into a xsd file with columns: xsd file; xsd element (N/A if the xsd had no element (e.g., only referecnces)); set of files using the element (N/A if the previous one was N/A)

## Issues
https://github.com/openTdataCH/xsd-element-coverage-checker\n


## Impressum
David Rudi, Matthias Günter
SBB

## License
MIT-License

## TODOs
* Does it sensibly work for complexTypes and groups !!
* Error handling
* Improve in program documentation
* Test it with OJP
* Does foundAMatch work as advertised
* Ignoring folders and files with a paremeter
* Testing that it works for imports
* Make sure namespaces are handled correctly
