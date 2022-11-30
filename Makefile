default:
	ant


run:
	java -jar SimpleDNS.jar -r 198.41.0.4 -e ec2.csv

clean:
	rm -rf ./bin
	rm SimpleDNS.jar