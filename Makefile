
OUT = SimpleDNS.jar
ROOT ?= 192.33.4.12
CSV ?= ec2.csv

default:
	ant

run: default
	java -jar ${OUT} -r ${ROOT} -e ${CSV}

clean:
	rm -rf ./bin
	rm SimpleDNS.jar