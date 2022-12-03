
OUT = SimpleDNS.jar
ROOT ?= 198.41.0.4 #128.104.222.9
CSV ?= ec2.csv



default:
	ant


run:
	ant
	java -jar ${OUT} -r ${ROOT} -e ${CSV}

clean:
	rm -rf ./bin
	rm SimpleDNS.jar