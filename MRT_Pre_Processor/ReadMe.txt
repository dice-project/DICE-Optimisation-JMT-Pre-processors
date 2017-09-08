MapReduce Template Pre-Processor

This tool generates a model with MRTs for JMT. It receives as input an XML file containing MRTs and outputs the model with the MRTs in a JSIMG file. The JSIMG file is the type of file used by JMT JSIMgraph to save/load models.

The following code gives an example of the MRT:

<template_mapreduce name="MapReduce 1">
	<input name="Queue 1"></input>
	<fork>
		<map>3</map>
		<red>3</red>
	</fork>
	<mapper>2</mapper>
	<semaphore>
		<class name="Class1">2</class>
	</semaphore>
	<reducer>2</reducer>
	<output name="Queue 2"></output>
</template_mapreduce>

input: the name of the component connected to the input of the MapReduce
fork: the fork degree for mappers and reducers
mapper: the number of queues as mappers
semaphore: the semaphore threshold for each class
reducer: the number of queues as reducers
output: the name of the component connected from the output of the MapReduce

Some examples of input and output files can be found in the examples folder.

In order to compile you should run:

javac -cp ".\lib\*" -d .\bin\ .\src\MRT_Pre_Processor.java

In order to execute you should run:

java -cp ".\bin;.\lib\*" MRT_Pre_Processor [PATH TO INPUT FILE] [PATH TO OUTPUT FILE]

For Linux, "\" and ";" should be replaced with "/" and ":" respectively.
