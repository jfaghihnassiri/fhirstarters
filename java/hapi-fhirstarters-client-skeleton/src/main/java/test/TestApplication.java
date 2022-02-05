package test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.*;

import java.util.LinkedList;
import java.util.List;

public class TestApplication {

   /**
    * This is the Java main method, which gets executed
    */
   public static void main(String[] args) {

      // Create a context
      FhirContext ctx = FhirContext.forR4();

      // Create a client
      IGenericClient client = ctx.newRestfulGenericClient("https://hapi.fhir.org/baseR4");

      // Read a patient with the given ID
      //Patient patient = client.read().resource(Patient.class).withId("example").execute();

      // Print the output
      //String string = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient);
      //System.out.println(string);

      Appointment propApp = new Appointment();
      // Basic info
      propApp.setStatus(Appointment.AppointmentStatus.PROPOSED);
      propApp.addIdentifier().setSystem("urn:system").setValue("12345");

      // Set the slot
      List<Reference> slots = new LinkedList<>();
      Schedule sched = getFirstFreeSchedule(client);
      Slot slot = getFirstFreeSlot(client, sched.getId());
      Print("First free ID came back as "+slot.getId()+" for schedule "+sched.getId());
      List<Reference> actors = sched.getActor();
      // TODO loop over actors
      slots.add(new Reference(slot));
      slots.get(0).setId(slot.getId());
      propApp.setSlot(slots);
      Print("Slot for propApp set to "+propApp.getSlot().get(0).getId());
      // Set the participants
      // TODO propApp.setParticipant


   }




   static Bundle getSchedules(IGenericClient client) {
      Bundle searchResultSched = client
         .search()
         .forResource(Schedule.class)
         .returnBundle(Bundle.class)
         .execute();

      return searchResultSched;
   }

   static Bundle getSlots(IGenericClient client, String scheduleId) {
      Bundle searchResultSlot = client
         .search()
         .forResource(Slot.class)
         .where(Slot.SCHEDULE.hasId(scheduleId))
         .returnBundle(Bundle.class)
         .execute();
      return searchResultSlot;
   }

   static Schedule getFirstFreeSchedule(IGenericClient client) {
      Bundle scheds = getSchedules(client);
      Print("Got " + scheds.getEntry().size() + " schedules");
      while(true) {
         for (int i =0; i<scheds.getEntry().size(); i++) {
            Print("Loop schedule " + i);
            Bundle.BundleEntryComponent sched = scheds.getEntry().get(i);
            Slot slot = getFirstFreeSlot(client, sched);
            if (slot != null) {
               return (Schedule)sched.getResource();
            }
         }
         if (scheds.getLink(Bundle.LINK_NEXT) == null) {
            break;
         } else {
            scheds = client.loadPage().next(scheds).execute();
         }
      }


      return null;
   }

   static Slot getFirstFreeSlot(IGenericClient client, Bundle.BundleEntryComponent sched) {
      return getFirstFreeSlot(client, sched.getId());
   }

   static Slot getFirstFreeSlot(IGenericClient client, String sched) {
      Bundle slots = getSlots(client, sched);
      Print("Got " + slots.getEntry().size() + " slots");
      while(true) {
         for (int j = 0; j < slots.getEntry().size(); j++) {
            Print("Loop slot " + j);
            Slot slot = (Slot) slots.getEntry().get(j).getResource();

            if (slot.getStatus().equals(Slot.SlotStatus.FREE)) {
               return slot;
            }
         }
         if (slots.getLink(Bundle.LINK_NEXT) == null) {
            break;
         } else {
            slots = client.loadPage().next(slots).execute();
         }
      }
      return null;
   }

   static void Print(String s) {
      System.out.println(s);
   }
}
