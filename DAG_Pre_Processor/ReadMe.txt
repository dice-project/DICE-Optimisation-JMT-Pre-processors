Direct Acyclic Graph Pre-Processor

This tool generates the model of a DAG for JMT. It receives as input a JSON file representing a DAG and outputs the model of the DAG in a JSIMG file. The JSIMG file is the type of file used by JMT JSIMgraph to save/load models.

An example of input and output files can be found in the examples folder.

In order to compile you should run:

javac -cp ".\lib\*" -d .\bin\ .\src\DAG_Pre_Processor.java

In order to execute you should run:

java -cp ".\bin;.\lib\*" DAG_Pre_Processor [PATH TO INPUT FILE] [PATH TO OUTPUT FILE]

For Linux, "\" and ";" should be replaced with "/" and ":" respectively.
