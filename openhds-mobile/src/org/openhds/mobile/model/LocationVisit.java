package org.openhds.mobile.model;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.openhds.mobile.Converter;
import org.openhds.mobile.OpenHDS;
import org.openhds.mobile.Queries;

import android.content.ContentResolver;
import android.database.Cursor;

/**
 * A LocationVisit represents a single visit to a specific location. This class
 * is built incrementally, meaning first the user constructs the location
 * hierarchy selection (region, sub-region, village, and round), one item at a
 * time. Then they must either set or create a location. After a location is
 * set, they can proceed to create a visit. Once a visit is created, the
 * LocationVisit cannot be changed, that is, a visit is considered started. Only
 * new events can be added to the location visit at this point. Once the user
 * completes a visit, a new LocationVisit is returned that contains pre-filled
 * location hierarchy selection (region, sub-region, village and round) assuming
 * the field worker will work in the same area.
 */
public class LocationVisit implements Serializable {

    private static final long serialVersionUID = -36602612353821830L;

    private FieldWorker fieldWorker;
    private LocationHierarchy region;
    private LocationHierarchy subRegion;
    private LocationHierarchy village;
    private Round round;

    private Location location;
    private Visit visit;

    private Individual selectedIndividual;

    public LocationVisit completeVisit() {
        LocationVisit visit = new LocationVisit();
        visit.fieldWorker = fieldWorker;
        visit.region = region;
        visit.subRegion = subRegion;
        visit.village = village;
        visit.round = round;

        return visit;
    }

    public FieldWorker getFieldWorker() {
        return fieldWorker;
    }

    public void setFieldWorker(FieldWorker fieldWorker) {
        this.fieldWorker = fieldWorker;
    }

    public LocationHierarchy getRegion() {
        return region;
    }

    public LocationHierarchy getSubRegion() {
        return subRegion;
    }

    public LocationHierarchy getVillage() {
        return village;
    }

    public Round getRound() {
        return round;
    }

    public Individual getSelectedIndividual() {
        return selectedIndividual;
    }

    public void setSelectedIndividual(Individual selectedIndividual) {
        this.selectedIndividual = selectedIndividual;
    }

    public void setRegion(LocationHierarchy region) {
        this.region = region;
        clearLevelsBelow(1);
    }

    public void clearLevelsBelow(int i) {
        switch (i) {
        case 0:
            region = null;
        case 1:
            subRegion = null;
        case 2:
            village = null;
        case 3:
            round = null;
        case 4:
            location = null;
        case 5:
            selectedIndividual = null;
        }
    }

    public void setSubRegion(LocationHierarchy subRegion) {
        this.subRegion = subRegion;
        clearLevelsBelow(2);
    }

    public void setVillage(LocationHierarchy village) {
        this.village = village;
        clearLevelsBelow(3);
    }

    public void setRound(Round round) {
        this.round = round;
        clearLevelsBelow(4);
    }

    public Location getLocation() {
        return location;
    }

    public int getLevelOfHierarchySelected() {
        if (region == null) {
            return 0;
        }

        if (subRegion == null) {
            return 1;
        }

        if (village == null) {
            return 2;
        }

        if (round == null) {
            return 3;
        }

        return 4;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void createLocation(ContentResolver resolver, String headId, String groupName) {
        StringBuilder builder = new StringBuilder();
        builder.append(headId.substring(0, 12));

        String baseString = builder.toString().substring(0, 9);
        Integer partToIncrement = Integer.parseInt(builder.toString().substring(9, 12));
        String locationId = generateLocationId(resolver, partToIncrement, baseString);

        location = new Location();
        location.setExtId(locationId);
        location.setHierarchy(village.getExtId());
        location.setName(groupName);
        location.setHead(headId);
        location.setUuid(UUID.randomUUID().toString());
    }

    public String generateLocationId(ContentResolver resolver, Integer partToIncrement, String baseString) {
        String temp = "";
        do {
            StringBuilder builder = new StringBuilder();
            partToIncrement++;
            if (partToIncrement.toString().length() == 1)
                builder.append("00").append(partToIncrement.toString());
            else if (partToIncrement.toString().length() == 2)
                builder.append("0").append(partToIncrement.toString());
            else if (partToIncrement.toString().length() == 3)
                builder.append(partToIncrement.toString());
            temp = baseString.concat(builder.toString());
        } while (Queries.hasLocationByExtId(resolver, temp));

        baseString = temp;
        return baseString;
    }

    public String generateIndividualId(ContentResolver resolver, Integer partToIncrement, String baseString) {
        String temp = "";
        do {
            StringBuilder builder = new StringBuilder();
            partToIncrement++;
            if (partToIncrement.toString().length() < 2)
                builder.append("0").append(partToIncrement.toString());
            if (partToIncrement.toString().length() == 2)
                builder.append(partToIncrement.toString());
            temp = baseString.concat(builder.toString());
        } while (Queries.individualByExtId(resolver, temp));

        baseString = temp;
        return baseString;
    }

    // this logic is specific for Cross River
    public void createVisit(ContentResolver resolver) {
        visit = new Visit();
        StringBuilder builder;
        int increment = 0;
        boolean result = false;

        do {
            builder = new StringBuilder();
            increment++;
            builder.append("V" + location.getExtId().substring(0, 9) + round.getRoundNumber()
                    + Integer.toString(increment) + location.getExtId().substring(9));
            result = findVisitByExtId(resolver, builder.toString());
        } while (result);

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String date = df.format(new Date());

        visit.setExtId(builder.toString());
        visit.setDate(date);
    }

    private boolean findVisitByExtId(ContentResolver resolver, String string) {
        Cursor cursor = resolver.query(OpenHDS.Visits.CONTENT_ID_URI_BASE,
                new String[] { OpenHDS.Visits.COLUMN_VISIT_EXTID }, OpenHDS.Visits.COLUMN_VISIT_EXTID + " = ?",
                new String[] { string }, null);

        boolean hasVisit = false;
        if (cursor.moveToFirst()) {
            hasVisit = true;
        }
        cursor.close();

        return hasVisit;
    }

    public PregnancyOutcome createPregnancyOutcome(ContentResolver resolver, Individual father) {
        PregnancyOutcome outcome = new PregnancyOutcome();
        outcome.setMother(selectedIndividual);
        outcome.setFather(father);

        // generation of child ids
        String motherId;
        String childId;
        try {
            motherId = selectedIndividual.getExtId();
            String householdSectionId = motherId.substring(12, 14);
            String locationId = selectedIndividual.getCurrentResidence();
            childId = locationId + householdSectionId;
        } catch (Exception e) {
            return null;
        }

        String baseString = childId;
        Integer partToIncrement = Integer.parseInt(motherId.substring(14, 16));

        String child1Id = generateIndividualId(resolver, partToIncrement, baseString);
        partToIncrement = Integer.parseInt(child1Id.substring(14, 16));
        String child2Id = generateIndividualId(resolver, partToIncrement, baseString);

        outcome.setChild1ExtId(child1Id);
        outcome.setChild2ExtId(child2Id);

        return outcome;
    }

    // an option to create a new social group rather than to reference an
    // existing one
    public SocialGroup createSocialGroup(ContentResolver resolver) {
        SocialGroup sg = new SocialGroup();
        String headId = selectedIndividual.getExtId();
        String baseString = headId.substring(0, 12);
        Integer partToIncrement = Integer.parseInt(headId.substring(12, 14));

        String socialgroupId = generateSocialGroupId(resolver, partToIncrement, baseString);

        sg.setExtId(socialgroupId);

        return sg;
    }

    public String generateSocialGroupId(ContentResolver resolver, Integer partToIncrement, String baseString) {
        String temp = "";
        do {
            StringBuilder builder = new StringBuilder();
            partToIncrement++;
            if (partToIncrement.toString().length() == 1)
                builder.append("0").append(partToIncrement.toString());
            else if (partToIncrement.toString().length() == 2)
                builder.append(partToIncrement.toString());
            temp = baseString.concat(builder.toString());
        } while (Queries.hasSocialGroupByExtId(resolver, temp));

        baseString = temp;
        return baseString;
    }

    public Individual determinePregnancyOutcomeFather(ContentResolver resolver) {
        Cursor cursor = Queries.getRelationshipByFemale(resolver, selectedIndividual.getExtId());
        List<Relationship> rels = Converter.toRelationshipList(cursor);
        DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

        Relationship current = null;
        // must find the most current relationship
        for (Relationship rel : rels) {
            if (current == null)
                current = rel;

            else {
                try {
                    Date currentDate = formatter.parse(current.getStartDate());
                    Date relDate = formatter.parse(rel.getStartDate());
                    if (currentDate.before(relDate))
                        current = rel;

                } catch (ParseException e) {
                    return null;
                }
            }
        }
        if (current == null)
            return null;
        else {
            String fatherId = current.getMaleIndividual();
            cursor = Queries.getIndividualByExtId(resolver, fatherId);
            return Converter.toIndividual(cursor);
        }
    }

    public Visit getVisit() {
        return visit;
    }

    public boolean isVisitStarted() {
        return visit != null;
    }
}
