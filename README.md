# Annotation Processors

# Event Processor

Checks methods annotated by SubscribeEvent if they belong on the bus
defined by @Mod.EventBusSubscriber. 
If @Mod is present, it will also check all non-static methods
If any of the methods are found to be configured incorrectly, the Processor
will report an error and explain how to fix.
