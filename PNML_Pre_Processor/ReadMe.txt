Petri Net Makeup Language Pre-Processor

This tool generates the model of a GSPN or SWN for JMT. It receives as input a PNML file representing a GSPN or SWN and outputs the model of the GSPN or SWN in a JSIMG file. The JSIMG file is the type of file used by JMT JSIMgraph to save/load models.

Some examples of input and output files can be found in the examples folder.

In order to compile you should run:

javac -cp "..\lib\*" -d .\bin\ .\src\PNML_Pre_Processor.java

In order to execute you should run:

java -cp ".\bin;..\lib\*" PNML_Pre_Processor gspn [PATH TO INPUT FILE] [PATH TO OUTPUT FILE] [PATH TO INDEX FILE]
java -cp ".\bin;..\lib\*" PNML_Pre_Processor swn-HadoopCap [PATH TO INPUT FILE] [PATH TO OUTPUT FILE]

For Linux, "\" and ";" should be replaced with "/" and ":" respectively.
