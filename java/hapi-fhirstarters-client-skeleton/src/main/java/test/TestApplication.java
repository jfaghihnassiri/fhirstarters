package test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
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

      // Create an appointment
      Appointment appointment = new Appointment();

      // Basic info
      appointment.setStatus(Appointment.AppointmentStatus.PROPOSED);
      appointment.addIdentifier().setSystem("urn:system").setValue("12345");

      // Find a free slot and set it. Save off the actors for the schedule.
      List<Reference> slots = new LinkedList<>();
      Schedule sched = getFirstFreeSchedule(client);
      Slot slot = getFirstFreeSlot(client, sched.getId());
      Print("First free ID came back as "+slot.getId()+" for schedule "+sched.getId());
      List<Reference> actors = sched.getActor();
      slots.add(new Reference(slot));
      slots.get(0).setId(slot.getId());
      appointment.setSlot(slots);
      Print("Slot for appointment set to "+appointment.getSlot().get(0).getId());

      // Find an active patient
      Patient patient = getFirstActivePatient(client);
      Print("Active patient came back as "+patient.getId());

      // Set the participants to the practitioner(s) and the patient
      List<Appointment.AppointmentParticipantComponent> participants = new LinkedList<>();
      for (int i = 0; i < actors.size(); i++) {
         participants.add(new Appointment.AppointmentParticipantComponent().setStatus(Appointment.ParticipationStatus.NEEDSACTION).setRequired(Appointment.ParticipantRequired.REQUIRED).setActor(actors.get(i)));
         Print("Practitioner or location for schedule added to participants list: "+actors.get(i).getId());
      }
      participants.add(new Appointment.AppointmentParticipantComponent().setStatus(Appointment.ParticipationStatus.NEEDSACTION).setRequired(Appointment.ParticipantRequired.REQUIRED).setActor(new Reference(patient)));
      Print("Patient added to participants list: "+patient.getId());
      appointment.setParticipant(participants);

      // Send out the appointment
      // Invoke the server create method and send pretty-printed JSON encoding to the server instead of the default which is non-pretty printed XML
      MethodOutcome outcome = client.create()
         .resource(appointment)
         .prettyPrint()
         .encodedJson()
         .execute();

      // The MethodOutcome object will contain information about the
      // response from the server, including the ID of the created
      // resource, the OperationOutcome response, etc. (assuming that
      // any of these things were provided by the server! They may not
      // always be)
      IIdType id = outcome.getId();
      System.out.println("Got ID: " + id.getValue());


   }


   static Bundle getPatients(IGenericClient client) {
      return client
         .search()
         .forResource(Patient.class)
         .returnBundle(Bundle.class)
         .execute();
   }

   static Patient getFirstActivePatient(IGenericClient client) {
      Bundle patients = getPatients(client);
      Print("Got " + patients.getEntry().size() + " patients");
      while(true) {
         for (int i =0; i<patients.getEntry().size(); i++) {
            Print("Loop patient " + i);
            Bundle.BundleEntryComponent patient = patients.getEntry().get(i);
            Patient patientObj = (Patient) patient.getResource();
            if (patientObj.getActive() == true) {
               Print("Found an active patient!");
               return patientObj;
            }
         }
         if (patients.getLink(Bundle.LINK_NEXT) == null) {
            break;
         } else {
            patients = client.loadPage().next(patients).execute();
         }
      }
      return null;
   }


   static Bundle getSchedules(IGenericClient client) {
      return client
         .search()
         .forResource(Schedule.class)
         .returnBundle(Bundle.class)
         .execute();
   }

   static Bundle getSlots(IGenericClient client, String scheduleId) {
      return client
         .search()
         .forResource(Slot.class)
         .where(Slot.SCHEDULE.hasId(scheduleId))
         .returnBundle(Bundle.class)
         .execute();
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
               Print("Found a free schedule!");
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
               Print("Found a free slot!");
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

   static void PrintJSON(FhirContext ctx, IBaseResource resource) {
      System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource));
   }
}
