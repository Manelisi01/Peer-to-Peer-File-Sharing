JFLAGS = -g
SRCDIR = src
BINDIR = bin
JC = javac

.SUFFIXES: .java .class

#.java.class:
#	$(JC) $(JFLAGS) -d $(BINDIR) $*.java
$(BINDIR)/%.class : $(SRCDIR)/%.java
	$(JC) -d $(BINDIR)/ $(SRCDIR)/*.java

CLASSES = \
	$(BINDIR)/Tracker.class \
	$(BINDIR)/Seeder.class \
	$(BINDIR)/Leecher.class
	
default: $(CLASSES)

classes: $(CLASSES:.java=.class)

clean:
	$(RM) $(BINDIR)/*.class
	
