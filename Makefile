.PHONY: run test package clean

run:
	mvn spring-boot:run

test:
	mvn test

package:
	mvn package

clean:
	mvn clean
	
