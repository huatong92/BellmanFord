JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	Neighbor.java \
	Packet.java \
	PacketAnalysisThread.java \
	FilePacket.java \
	ReadKeyboardThread.java \
	SendThread.java	\
	bfclient.java \

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class