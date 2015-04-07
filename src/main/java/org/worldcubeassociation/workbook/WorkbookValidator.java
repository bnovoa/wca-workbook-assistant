package org.worldcubeassociation.workbook;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.worldcubeassociation.db.Database;
import org.worldcubeassociation.db.Person;
import org.worldcubeassociation.workbook.parse.CellFormatter;
import org.worldcubeassociation.workbook.parse.CellParser;
import org.worldcubeassociation.workbook.parse.ParsedGender;
import org.worldcubeassociation.workbook.parse.RowTokenizer;
import org.worldcubeassociation.workbook.scrambles.RoundScrambles;
import org.worldcubeassociation.workbook.scrambles.Scrambles;
import org.worldcubeassociation.workbook.scrambles.TNoodleSheetJson;

import java.text.ParseException;
import java.util.*;

/**
 * @author Lars Vandenbergh
 */
public class WorkbookValidator {

    private static final String[] ORDER = {"1st", "2nd", "3rd", "4th", "5th"};
    public static final int TEN_MINUTES = 600;

    public static void validate(MatchedWorkbook aMatchedWorkbook, Database aDatabase, Scrambles scrambles) {
        // Find registration sheet.
        MatchedSheet registrationSheet = null;
        for (MatchedSheet matchedSheet : aMatchedWorkbook.sheets()) {
            matchedSheet.getValidationErrors().clear();
            if (matchedSheet.getSheetType() == SheetType.REGISTRATIONS) {
                if (registrationSheet == null) {
                    registrationSheet = matchedSheet;
                }
                else {
                    matchedSheet.getValidationErrors().add(new ValidationError(Severity.HIGH,
                            "Workbook can have only one registration sheet, ignoring this sheet",
                            matchedSheet, -1, ValidationError.SHEET_TYPE_CELL_IDX));
                }
            }
        }

        // Validate registration sheet.
        aMatchedWorkbook.getPersons().clear();
        if (registrationSheet != null) {
            validateRegistrationsSheet(registrationSheet, aMatchedWorkbook, aDatabase);
        }

        // Validate results sheets.
        for (MatchedSheet matchedSheet : aMatchedWorkbook.sheets()) {
            if (matchedSheet.getSheetType() == SheetType.RESULTS) {
                validateResultsSheet(matchedSheet, aMatchedWorkbook, aDatabase, scrambles);
            }
        }
    }

    public static void validateSheetsForEvent(MatchedWorkbook aMatchedWorkbook, Event aEvent, Database aDatabase, Scrambles scrambles) {
        for (MatchedSheet matchedSheet : aMatchedWorkbook.sheets()) {
            if (matchedSheet.getSheetType() == SheetType.RESULTS && aEvent.equals(matchedSheet.getEvent())) {
                validateResultsSheet(matchedSheet, aMatchedWorkbook, aDatabase, scrambles);
            }
        }
    }


	public static void validateSheetsWithRoundScrambles(MatchedWorkbook matchedWorkbook, RoundScrambles newRoundScrambles, Database database, Scrambles scrambles) {
        for (MatchedSheet matchedSheet : matchedWorkbook.sheets()) {
            if (matchedSheet.getSheetType() == SheetType.RESULTS && newRoundScrambles == matchedSheet.getRoundScrambles()) {
                validateResultsSheet(matchedSheet, matchedWorkbook, database, scrambles);
            }
        }
	}

    public static void validateSheet(MatchedSheet aMatchedSheet, MatchedWorkbook aMatchedWorkbook, Database aDatabase, Scrambles scrambles) {
        if (aMatchedSheet.getSheetType() == SheetType.REGISTRATIONS) {
            validateRegistrationsSheet(aMatchedSheet, aMatchedWorkbook, aDatabase);
        }
        else if (aMatchedSheet.getSheetType() == SheetType.RESULTS) {
            validateResultsSheet(aMatchedSheet, aMatchedWorkbook, aDatabase, scrambles);
        }
    }

    public static void validateRegistrationsSheet(MatchedSheet aMatchedSheet, MatchedWorkbook aMatchedWorkbook, Database aDatabase) {
        // Clear validation errors.
        aMatchedSheet.getValidationErrors().clear();
        aMatchedSheet.setValidated(false);

        // Clear persons
        aMatchedWorkbook.getPersons().clear();

        List<RegisteredPerson> persons = new ArrayList<RegisteredPerson>();
        Set<RegisteredPerson> duplicatePersons = new HashSet<RegisteredPerson>();

        // Validate name, country.
        Sheet sheet = aMatchedSheet.getSheet();
        for (int rowIdx = aMatchedSheet.getFirstDataRow(); rowIdx <= aMatchedSheet.getLastDataRow(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);

            int headerColIdx = aMatchedSheet.getNameHeaderColumn();
            String name = CellParser.parseMandatoryText(row.getCell(headerColIdx));
            if (name == null) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Missing name", aMatchedSheet, rowIdx, headerColIdx);
                aMatchedSheet.getValidationErrors().add(validationError);
            }
            else{
                // Check spelling of name.
                if (!Character.isUpperCase(name.codePointAt(0))) {
                    ValidationError validationError = new ValidationError(Severity.HIGH, "Name does not start with capital letter", aMatchedSheet, rowIdx, 1);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
                if (name.matches(".*[ \\t\\r\\n\\v\\f]{2}.*")) {
                    ValidationError validationError = new ValidationError(Severity.HIGH, "Name contains double spaces", aMatchedSheet, rowIdx, 1);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
                if (name.matches(".*\\([ \\t\\r\\n\\v\\f].*")) {
                    ValidationError validationError = new ValidationError(Severity.HIGH, "Localized part of name starts with space", aMatchedSheet, rowIdx, 1);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
                if (name.matches(".*[ \\t\\r\\n\\v\\f]\\).*")) {
                    ValidationError validationError = new ValidationError(Severity.HIGH, "Localized part of name ends with space", aMatchedSheet, rowIdx, 1);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
                if (name.matches(".*[^ \\t\\r\\n\\v\\f]\\(.*")) {
                    ValidationError validationError = new ValidationError(Severity.HIGH, "No space between Latinized and localized part of name", aMatchedSheet, rowIdx, 1);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
            }

            int countryColIdx = aMatchedSheet.getCountryHeaderColumn();
            String country = CellParser.parseMandatoryText(row.getCell(countryColIdx));
            if (country == null) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Missing country", aMatchedSheet, rowIdx, countryColIdx);
                aMatchedSheet.getValidationErrors().add(validationError);
            }
            else if (aDatabase != null && aDatabase.getCountries().findById(country) == null) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Country not found in WCA database", aMatchedSheet, rowIdx, countryColIdx);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            int genderColIdx = aMatchedSheet.getGenderHeaderColumn();
            ParsedGender gender = CellParser.parseGender(row.getCell(genderColIdx));
            if (gender == null) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Misformatted gender", aMatchedSheet, rowIdx, genderColIdx);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            int dobColIdx = aMatchedSheet.getDobHeaderColumn();
            Cell dobCell = row.getCell(dobColIdx);
            Date dob = CellParser.parseDateCell(dobCell);
            String dobText = CellParser.parseText(dobCell, false);
            if (dobText != null && dobText.length() > 0) {
                if (dob == null) {
                    ValidationError validationError = new ValidationError(Severity.HIGH, "Misformatted date of birth", aMatchedSheet, rowIdx, dobColIdx);
                    aMatchedSheet.getValidationErrors().add(validationError);
                } else {
                    double age = (System.currentTimeMillis() - dob.getTime()) / (1000 * 60 * 60 * 24 * 365.25);
                    if (age < 5 || age > 90) {
                        ValidationError validationError = new ValidationError(Severity.LOW, "Incorrect date of birth?", aMatchedSheet, rowIdx, dobColIdx);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                }
            }

            int wcaColIdx = aMatchedSheet.getWcaIdHeaderColumn();
            String wcaId = CellParser.parseOptionalText(row.getCell(wcaColIdx));
            boolean wcaIdEmpty = "".equals(wcaId);
            boolean wcaIdValid = validateWCAId(wcaId);
            if (!wcaIdEmpty && !wcaIdValid) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Misformatted WCA id", aMatchedSheet, rowIdx, 3);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            // If we have a name and a country, check that the name, country and WCA ID is distinguishable from other
            // persons.
            if (name != null && country != null) {
                RegisteredPerson person = new RegisteredPerson(rowIdx, name, country, wcaId);
                int existingPersonIdx = persons.indexOf(person);
                if (existingPersonIdx >= 0) {
                    duplicatePersons.add(person);
                    duplicatePersons.add(persons.get(existingPersonIdx));
                }
                persons.add(person);
            }

            // If we have a valid WCA id check that it is in the database and check that the name and country matches
            // what what is in the database.
            if (wcaIdValid && aDatabase != null) {
                Person person = aDatabase.getPersons().findById(wcaId);
                if (person == null) {
                    ValidationError validationError = new ValidationError(Severity.HIGH, "Unknown WCA id", aMatchedSheet, rowIdx, 3);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
                else {
                    if (name != null && !name.equals(person.getName())) {
                        ValidationError validationError = new ValidationError(Severity.LOW, "Name does not match name in WCA database: " + person.getName(), aMatchedSheet, rowIdx, 1);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                    if (country != null && !country.equals(person.getCountry())) {
                        ValidationError validationError = new ValidationError(Severity.LOW, "Country does not match country in WCA database: " + person.getCountry(), aMatchedSheet, rowIdx, 2);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                }
            }

            // Check that newcomers (no WCA ID) do not match existing persons
            if (wcaIdEmpty && name != null && country != null && aDatabase != null) {
                List<Person> existing = aDatabase.getPersons().findByNameAndCountry(name, country);
                if (!existing.isEmpty()) {
                    ValidationError validationError = new ValidationError(Severity.LOW, "Name and country match existing person, WCA ID missing?", aMatchedSheet, rowIdx, 3);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
            }
        }

        // Report persons that can't be distinguished from each other.
        for (RegisteredPerson duplicatePerson : duplicatePersons) {
            ValidationError validationError = new ValidationError(Severity.HIGH,
                    "Duplicate name, country and WCA id", aMatchedSheet, duplicatePerson.getRow(), -1);
            aMatchedSheet.getValidationErrors().add(validationError);
        }

        aMatchedSheet.setValidated(true);

        aMatchedWorkbook.getPersons().addAll(persons);
    }

    public static void validateResultsSheet(MatchedSheet aMatchedSheet,
                                            MatchedWorkbook aMatchedWorkbook,
                                            Database aDatabase,
                                            Scrambles scrambles) {
        // Clear validation errors.
        aMatchedSheet.getValidationErrors().clear();
        aMatchedSheet.setValidated(false);

        // Check that every round has scrambles.
        // Currently, we don't make any effort to notify users about unused scrambles,
        // or the same scrambles being applied to different rounds.  
        RoundScrambles roundScrambles = aMatchedSheet.getRoundScrambles();
        boolean missingScrambles;
        if(roundScrambles == null) {
            missingScrambles = true;
        } else {
            missingScrambles = roundScrambles.getSheetsExcludingDeleted().size() == 0;
        }
        if(missingScrambles) {
	        ValidationError missingScramblesError = new ValidationError(Severity.LOW, "Missing scrambles", aMatchedSheet, -1, ValidationError.ROUND_SCRAMBLES_CELL_IDX);
	        aMatchedSheet.getValidationErrors().add(missingScramblesError);
        } else {
        	// The round has scrambles, lets make sure we have the correct number of scrambles.
        	List<TNoodleSheetJson> scrambleSheets = roundScrambles.getSheetsExcludingDeleted();

            // Check that that the groupIds for these scrambles are unique
        	HashSet<String> seenGroupsIds = new HashSet<String>();
        	HashSet<String> duplicateGroupIds = new HashSet<String>();
        	for(TNoodleSheetJson scrambleSheet : scrambleSheets) {
        	    if(seenGroupsIds.contains(scrambleSheet.group)) {
        	        duplicateGroupIds.add(scrambleSheet.group);
        	    }
        	    seenGroupsIds.add(scrambleSheet.group);
        	}
        	for(String duplicateGroupId : duplicateGroupIds) {
                String msg = "Duplicate scramble group " + duplicateGroupId;
                ValidationError duplicateGroupError = new ValidationError(Severity.HIGH, msg, aMatchedSheet, -1, ValidationError.ROUND_SCRAMBLES_CELL_IDX);
                aMatchedSheet.getValidationErrors().add(duplicateGroupError);
        	}

        	// ***** HACK ALERT *****
        	// Here we assume that the groups generated for multiblind were used for each of the attempts.
        	// See https://github.com/cubing/wca-workbook-assistant/issues/74#issuecomment-36166131 for
        	// the full discussion.
    		if(aMatchedSheet.getEvent() == Event._333mbf) {
    			int actualCount = scrambleSheets.size();
				int expectedCount = aMatchedSheet.getFormat().getResultCount();
				if(actualCount != expectedCount) {
					String msg = "Incorrect number of attempts (groups are treated as attempts) for " + aMatchedSheet.getEventId() + " round: " + aMatchedSheet.getRound() + " (found: " + actualCount + ", expected: " + expectedCount + ")";
					ValidationError missingScramblesError = new ValidationError(Severity.HIGH, msg, aMatchedSheet, -1, ValidationError.ROUND_SCRAMBLES_CELL_IDX);
					aMatchedSheet.getValidationErrors().add(missingScramblesError);
				}
    		} else {
    			for(TNoodleSheetJson sheet : scrambleSheets) {
    				int actualCount = sheet.scrambles.length;
    				int expectedCount = aMatchedSheet.getFormat().getResultCount();
    				if(actualCount != expectedCount) {
    					String msg = "Incorrect number of scrambles in group: " + sheet.group + " (found: " + actualCount + ", expected: " + expectedCount + ")";
    					ValidationError missingScramblesError = new ValidationError(Severity.HIGH, msg, aMatchedSheet, -1, ValidationError.ROUND_SCRAMBLES_CELL_IDX);
    					aMatchedSheet.getValidationErrors().add(missingScramblesError);
    				}
    			}
    		}

            // Check for duplicate scrambles.
            List<MatchedSheet> sheets = aMatchedWorkbook.sheets();
            List<MatchedSheet> duplicateScrambles = new ArrayList<MatchedSheet>();
            for (MatchedSheet sheet : sheets) {
                if (sheet.getRoundScrambles() == roundScrambles) {
                	duplicateScrambles.add(sheet);
                }
            }

            if (duplicateScrambles.size() > 1) {
            	StringBuilder sheetNames = new StringBuilder();
                for (int i = 0, duplicateScramblesSize = duplicateScrambles.size(); i < duplicateScramblesSize; i++) {
                    if (i == duplicateScramblesSize - 1) {
                        sheetNames.append(" and ");
                    }
                    else if (i > 0) {
                        sheetNames.append(", ");
                    }
                    MatchedSheet duplicateSheet = duplicateScrambles.get(i);
                    sheetNames.append(duplicateSheet.getSheet().getSheetName());
                }
                ValidationError validationError = new ValidationError(Severity.HIGH, "Duplicate scrambles for sheets " + sheetNames, aMatchedSheet, -1, ValidationError.ROUND_SCRAMBLES_CELL_IDX);
                aMatchedSheet.getValidationErrors().add(validationError);
            }
        }

        // Validate round, event, format and result format.
        if (aMatchedSheet.getEvent() == null) {
            ValidationError validationError = new ValidationError(Severity.HIGH, "Missing event", aMatchedSheet, -1, ValidationError.EVENT_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }
        if (aMatchedSheet.getRound() == null) {
            ValidationError validationError = new ValidationError(Severity.HIGH, "Missing round", aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }
        if (aMatchedSheet.getFormat() == null) {
            ValidationError validationError = new ValidationError(Severity.HIGH, "Missing format", aMatchedSheet, -1, ValidationError.FORMAT_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }
        if (aMatchedSheet.getResultFormat() == null) {
            ValidationError validationError = new ValidationError(Severity.HIGH, "Missing result format", aMatchedSheet, -1, ValidationError.RESULT_FORMAT_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }

        boolean validRoundFormat = true;
        if (aMatchedSheet.getEvent() != null && aMatchedSheet.getFormat() != null &&
                !FormatValidator.isValidRoundFormat(aMatchedSheet.getFormat(), aMatchedSheet.getEvent())) {
            ValidationError validationError = new ValidationError(Severity.HIGH, "Illegal format for event", aMatchedSheet, -1, ValidationError.FORMAT_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
            validRoundFormat = false;
        }

        boolean validResultFormat = true;
        if (aMatchedSheet.getEvent() != null && aMatchedSheet.getResultFormat() != null) {
            if (aMatchedSheet.getEvent() == Event._333mbf || aMatchedSheet.getEvent() == Event._333fm) {
                validResultFormat = aMatchedSheet.getResultFormat() == ResultFormat.NUMBER;
            }
            else {
                validResultFormat = aMatchedSheet.getResultFormat() != ResultFormat.NUMBER;
            }
            if (!validResultFormat) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Illegal result format for event", aMatchedSheet, -1, ValidationError.RESULT_FORMAT_CELL_IDX);
                aMatchedSheet.getValidationErrors().add(validationError);
            }
        }
        // Check for duplicate event/round combination.
        if (aMatchedSheet.getEvent() != null && aMatchedSheet.getRound() != null) {
            List<MatchedSheet> sheets = aMatchedWorkbook.sheets();
            List<MatchedSheet> duplicateSheets = new ArrayList<MatchedSheet>();
            for (MatchedSheet sheet : sheets) {
                if (aMatchedSheet.getEvent().equals(sheet.getEvent()) &&
                        aMatchedSheet.getRound().isSameRoundAs(sheet.getRound())) {
                    duplicateSheets.add(sheet);
                }
            }

            if (duplicateSheets.size() > 1) {
                StringBuffer sheetNames = new StringBuffer();
                for (int i = 0, duplicateSheetsSize = duplicateSheets.size(); i < duplicateSheetsSize; i++) {
                    if (i == duplicateSheetsSize - 1) {
                        sheetNames.append(" and ");
                    }
                    else if (i > 0) {
                        sheetNames.append(", ");
                    }
                    MatchedSheet duplicateSheet = duplicateSheets.get(i);
                    sheetNames.append(duplicateSheet.getSheet().getSheetName());
                }
                ValidationError validationError = new ValidationError(Severity.HIGH, "Duplicate round for event in sheets " + sheetNames, aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
                aMatchedSheet.getValidationErrors().add(validationError);
            }
            else {
                // Check if round naming complies with WCA website guidelines.
                List<MatchedSheet> sameEventRounds = new ArrayList<MatchedSheet>();
                for (MatchedSheet sheet : sheets) {
                    if (aMatchedSheet.getEvent().equals(sheet.getEvent())) {
                        sameEventRounds.add(sheet);
                    }
                }

                Collections.sort(sameEventRounds, new DecreasingCompetitorCountComparator());
                int roundIndex = sameEventRounds.indexOf(aMatchedSheet);
                Round round = aMatchedSheet.getRound();
                boolean combined = round.isCombined();

                if (sameEventRounds.size() == 1) {
                    Round expectedOnlyRound = combined ? Round.COMBINED_FINAL : Round.FINAL;
                    if (round != expectedOnlyRound) {
                        ValidationError validationError = new ValidationError(Severity.HIGH, "Only round of event should be called " + expectedOnlyRound.toString(), aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                }
                else {
                    Round expectedFirstRound = combined ? Round.COMBINED_FIRST_ROUND : Round.FIRST_ROUND;
                    if (roundIndex == 0 && round != expectedFirstRound) {
                        ValidationError validationError = new ValidationError(Severity.HIGH, "First round of event should be called " + expectedFirstRound.toString(), aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }

                    Round expectedLastRound = combined ? Round.COMBINED_FINAL : Round.FINAL;
                    if (roundIndex == sameEventRounds.size() - 1 && round != expectedLastRound) {
                        ValidationError validationError = new ValidationError(Severity.HIGH, "Last round of event should be called " + expectedLastRound.toString(), aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }

                    if (sameEventRounds.size() == 3) {
                        Round expectedSecondRound = combined ? Round.COMBINED_SECOND_ROUND : Round.SECOND_ROUND;
                        if (roundIndex == 1 && round != expectedSecondRound) {
                            ValidationError validationError = new ValidationError(Severity.HIGH, "Second round of event should be called " + expectedSecondRound.toString(), aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
                            aMatchedSheet.getValidationErrors().add(validationError);
                        }
                    }
                    else if (sameEventRounds.size() == 4) {
                        Round expectedThirdRound = combined ? Round.COMBINED_THIRD_ROUND : Round.SEMI_FINAL;
                        if (roundIndex == 2 && round != expectedThirdRound) {
                            ValidationError validationError = new ValidationError(Severity.HIGH, "Third round of event should be called " + expectedThirdRound.toString(), aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
                            aMatchedSheet.getValidationErrors().add(validationError);
                        }
                    }
                }
            }
        }

        // Validate position, name, country and WCA ID.
        Sheet sheet = aMatchedSheet.getSheet();
        FormulaEvaluator formulaEvaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        List<RegisteredPerson> persons = new ArrayList<RegisteredPerson>();
        Set<RegisteredPerson> duplicatePersons = new HashSet<RegisteredPerson>();
        for (int rowIdx = aMatchedSheet.getFirstDataRow(); rowIdx <= aMatchedSheet.getLastDataRow(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);

            Long position = CellParser.parsePosition(row.getCell(0));
            if (position == null) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Missing position", aMatchedSheet, rowIdx, 0);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            String name = CellParser.parseMandatoryText(row.getCell(1));
            if (name == null) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Missing name", aMatchedSheet, rowIdx, 1);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            String country = CellParser.parseMandatoryText(row.getCell(2));
            if (country == null) {
                ValidationError validationError = new ValidationError(Severity.HIGH, "Missing country", aMatchedSheet, rowIdx, 2);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            String wcaId = CellParser.parseOptionalText(row.getCell(3));

            // If we have a name and a country, check that the name, country and WCA id are unique within this round and
            // that they match with a row in the registration sheet.
            if (name != null && country != null) {
                RegisteredPerson person = new RegisteredPerson(rowIdx, name, country, wcaId);
                int existingPersonIdx = persons.indexOf(person);
                if (existingPersonIdx >= 0) {
                    duplicatePersons.add(person);
                    duplicatePersons.add(persons.get(existingPersonIdx));
                }
                persons.add(person);

                if (!aMatchedWorkbook.getPersons().contains(person)) {
                    ValidationError validationError = new ValidationError(Severity.LOW,
                            "Name, country and WCA id do not match any row in registration sheet", aMatchedSheet, rowIdx, -1);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
            }
        }

        // Report persons that can't be distinguished from each other.
        for (RegisteredPerson duplicatePerson : duplicatePersons) {
            ValidationError validationError = new ValidationError(Severity.HIGH,
                    "Duplicate name, country and WCA id", aMatchedSheet, duplicatePerson.getRow(), -1);
            aMatchedSheet.getValidationErrors().add(validationError);
        }

        // Validate results.
        if (aMatchedSheet.getEvent() != null &&
                aMatchedSheet.getRound() != null &&
                aMatchedSheet.getFormat() != null &&
                aMatchedSheet.getResultFormat() != null &&
                validRoundFormat &&
                validResultFormat) {
            validateResults(aMatchedSheet, formulaEvaluator, aDatabase);
        }

        aMatchedSheet.setValidated(true);
    }

    private static void validateResults(MatchedSheet aMatchedSheet, FormulaEvaluator aFormulaEvaluator, Database aDatabase) {
        List<ValidationError> validationErrors = aMatchedSheet.getValidationErrors();
        Sheet sheet = aMatchedSheet.getSheet();
        Event event = aMatchedSheet.getEvent();
        Round round = aMatchedSheet.getRound();
        Format format = aMatchedSheet.getFormat();
        ResultFormat resultFormat = aMatchedSheet.getResultFormat();
        ColumnOrder columnOrder = aMatchedSheet.getColumnOrder();

        boolean allRowsValid = true;
        int nbDataRows = aMatchedSheet.getLastDataRow() - aMatchedSheet.getFirstDataRow() + 1;
        Long[] bestResults = new Long[nbDataRows];
        Long[] averageResults = new Long[nbDataRows];
        boolean allCellsPresent = true;

        for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
            Row row = sheet.getRow(rowIdx + aMatchedSheet.getFirstDataRow());
            int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();

            // Validate individual results.
            boolean allResultsInRowValid = true;
            boolean allResultsInRowPresent = true;
            Long[] results = new Long[format.getResultCount()];
            for (int resultIdx = 1; resultIdx <= format.getResultCount(); resultIdx++) {
                int resultCellCol = RowTokenizer.getResultCell(resultIdx, format, event, columnOrder);
                Cell resultCell = row.getCell(resultCellCol);

                try {
                    Long result;
                    if (round.isCombined() && resultIdx > 1) {
                        result = CellParser.parseOptionalSingleTime(resultCell, resultFormat, event, aFormulaEvaluator);
                    }
                    else {
                        result = CellParser.parseMandatorySingleTime(resultCell, resultFormat, event, aFormulaEvaluator);
                    }
                    results[resultIdx - 1] = result;

                    if (result == 0) {
                        allResultsInRowPresent = false;
                    }
                    else {
                        if ((resultFormat == ResultFormat.SECONDS || resultFormat == ResultFormat.MINUTES) &&
                                !ResultsAggregator.roundAverage(result).equals(result)) {
                            validationErrors.add(new ValidationError(Severity.HIGH, ORDER[resultIdx - 1] + " result is over 10 minutes and should be rounded to the nearest second",
                                    aMatchedSheet, sheetRow, resultCellCol));
                            allResultsInRowValid = false;
                            allRowsValid = false;
                        }
                    }
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError(Severity.HIGH, ORDER[resultIdx - 1] + " result: " + e.getMessage(),
                            aMatchedSheet, sheetRow, resultCellCol));
                    allResultsInRowValid = false;
                    allRowsValid = false;
                }
            }

            if(!allResultsInRowPresent) {
            	allCellsPresent = false;
            }

            // For multiple blindfolded, check that the score is calculated correctly.
            if (event == Event._333mbf) {
                for (int resultIdx = 1; resultIdx <= format.getResultCount(); resultIdx++) {
                    int resultCellCol = RowTokenizer.getResultCell(resultIdx, format, event, columnOrder);
                    Long result = results[resultIdx - 1];

                    // Validate # tried cubes, solve cubes and seconds individually.
                    int cubesTriedCol = RowTokenizer.getCubesTriedCell(resultIdx);
                    Cell cubesTriedCell = row.getCell(cubesTriedCol);
                    int cubesSolvedCol = RowTokenizer.getCubesSolvedCell(resultIdx);
                    Cell cubesSolvedCell = row.getCell(cubesSolvedCol);
                    int secondsCol = RowTokenizer.getSecondsCell(resultIdx);
                    Cell secondsCell = row.getCell(secondsCol);

                    boolean cubesTriedValid = true;
                    Long cubesTried = null;
                    try {
                        cubesTried = CellParser.parseOptionalSingleTime(cubesTriedCell, ResultFormat.NUMBER, event, aFormulaEvaluator);

                        if (cubesTried == null || cubesTried == 0) {
                            if (resultIdx == 1 || !round.isCombined()) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "# tried cubes: missing value", aMatchedSheet, sheetRow, cubesTriedCol));
                                cubesTriedValid = false;
                            }
                        }
                        else if (cubesTried != -2 && cubesTried != -1 && cubesTried < 2) {
                            validationErrors.add(new ValidationError(Severity.HIGH, "# tried cubes should be at least 2", aMatchedSheet, sheetRow, cubesTriedCol));
                            cubesTriedValid = false;
                        }
                    }
                    catch (ParseException e) {
                        validationErrors.add(new ValidationError(Severity.HIGH, "# tried cubes: " + e.getMessage(), aMatchedSheet, sheetRow, cubesTriedCol));
                        cubesTriedValid = false;
                    }

                    Long cubesSolved = null;
                    boolean cubesSolvedValid = true;
                    try {
                        cubesSolved = CellParser.parseOptionalSingleTime(cubesSolvedCell, ResultFormat.NUMBER, event, aFormulaEvaluator);

                        if (cubesSolved == null) {
                            if (cubesTriedValid) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "# solved cubes: missing value", aMatchedSheet, sheetRow, cubesSolvedCol));
                                cubesSolvedValid = false;
                            }
                        }
                        else if (cubesSolved < 0) {
                            validationErrors.add(new ValidationError(Severity.HIGH, "# solved cubes should be a positive number", aMatchedSheet, sheetRow, cubesSolvedCol));
                            cubesSolvedValid = false;
                        }
                    }
                    catch (ParseException e) {
                        validationErrors.add(new ValidationError(Severity.HIGH, "# solved cubes: " + e.getMessage(), aMatchedSheet, sheetRow, cubesSolvedCol));
                        cubesSolvedValid = false;
                    }

                    Long seconds = null;
                    boolean secondsValid = true;
                    try {
                        seconds = CellParser.parseOptionalSingleTime(secondsCell, ResultFormat.NUMBER, event, aFormulaEvaluator);

                        if (seconds == null) {
                            if (cubesTriedValid && cubesSolvedValid && (cubesTried - cubesSolved <= cubesSolved)) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "Seconds: missing value", aMatchedSheet, sheetRow, secondsCol));
                                secondsValid = false;
                            }
                        }
                        else if (seconds < 0) {
                            validationErrors.add(new ValidationError(Severity.HIGH, "Seconds should be a positive number", aMatchedSheet, sheetRow, secondsCol));
                            secondsValid = false;
                        }
                        else if (seconds > 3600) {
                            validationErrors.add(new ValidationError(Severity.HIGH, "Seconds should not exceed 3600 (1 hour)", aMatchedSheet, sheetRow, secondsCol));
                            secondsValid = false;
                        }
                    }
                    catch (ParseException e) {
                        validationErrors.add(new ValidationError(Severity.HIGH, "Seconds: " + e.getMessage(), aMatchedSheet, sheetRow, secondsCol));
                        secondsValid = false;
                    }

                    // If # tried cubes, solve cubes and seconds are valid, calculate and check score and also check time limit.
                    if (cubesTriedValid && cubesSolvedValid && secondsValid && result != null) {
                        if (cubesTried == null || cubesTried == 0) {
                            if (result != 0) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "Score should be empty if # tried cubes is empty", aMatchedSheet, sheetRow, cubesTriedCol));
                                allResultsInRowValid = false;
                                allRowsValid = false;
                            }
                        }
                        else if (cubesTried == -2) {
                            if (result != -2) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "Score should be -2 if # tried cubes is DNS", aMatchedSheet, sheetRow, resultCellCol));
                                allResultsInRowValid = false;
                                allRowsValid = false;
                            }
                        }
                        else if (cubesTried == -1) {
                            if (result != -1) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "Score should be -1 if # tried cubes is DNF", aMatchedSheet, sheetRow, resultCellCol));
                                allResultsInRowValid = false;
                                allRowsValid = false;
                            }
                        }
                        else if (cubesTried == 2 && cubesSolved == 1) {
                            if (result != -1) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "Score should be -1 if # tried cubes is 2 and # solved cubes is 1", aMatchedSheet, sheetRow, resultCellCol));
                                allResultsInRowValid = false;
                                allRowsValid = false;
                            }
                        }
                        else {
                            long cubesUnsolved = cubesTried - cubesSolved;
                            if (cubesSolved >= cubesUnsolved) {
                                long cubeScore = 99 - (cubesSolved - cubesUnsolved);
                                long expectedScore = cubeScore * 10000000 + 100 * seconds + cubesUnsolved;
                                if (result != expectedScore) {
                                    validationErrors.add(new ValidationError(Severity.HIGH, "Score does not match calculated score: " + expectedScore, aMatchedSheet, sheetRow, resultCellCol));
                                    allResultsInRowValid = false;
                                    allRowsValid = false;
                                }

                                if (seconds > cubesTried * TEN_MINUTES) {
                                    validationErrors.add(new ValidationError(Severity.HIGH, "Time should not exceed 10 minutes times # tried cubes", aMatchedSheet, sheetRow, secondsCol));
                                    allResultsInRowValid = false;
                                    allRowsValid = false;
                                }

                                // Give a warning for unlikely low times (average per tried cube faster than 3x3x3 blindfolded single world record)
                                double avgPerTriedCubeSeconds = seconds.doubleValue() / cubesTried.doubleValue();
                                double singleWrSeconds = aDatabase.getSingleRanks().findByEvent("333bf").get(0).getBest() / 100.0;
                                if(avgPerTriedCubeSeconds < singleWrSeconds) {
                                    validationErrors.add(new ValidationError(Severity.LOW, "Unlikely low time for # tried cubes", aMatchedSheet, sheetRow, secondsCol));
                                }

                            }
                            else {
                                if (result != -1) {
                                    validationErrors.add(new ValidationError(Severity.HIGH, "Score should be -1 if # unsolved cubes is larger then # solved cubes", aMatchedSheet, sheetRow, resultCellCol));
                                    allResultsInRowValid = false;
                                    allRowsValid = false;
                                }
                            }
                        }
                    }
                    else {
                        allResultsInRowValid = false;
                        allRowsValid = false;
                    }
                }
            }

            // Check for only DNS rows.
            if (allResultsInRowValid) {
                boolean onlyDNS = true;
                for (int i = 0, resultsLength = results.length; i < resultsLength && onlyDNS; i++) {
                    Long result = results[i];
                    if (result != null && result != 0 && result != -2) {
                        onlyDNS = false;
                    }
                }

                if (onlyDNS) {
                    validationErrors.add(new ValidationError(Severity.HIGH, "Rows with only DNS results should be removed", aMatchedSheet, sheetRow, -1));
                }
            }

            // Validate best result.
            if (format.getResultCount() > 1) {
                int bestCellCol = RowTokenizer.getBestCell(format, event, columnOrder);
                Cell bestResultCell = row.getCell(bestCellCol);

                try {
                    Long bestResult = CellParser.parseMandatorySingleTime(bestResultCell, resultFormat, event, aFormulaEvaluator);
                    bestResults[rowIdx] = bestResult;
                    if (allResultsInRowValid) {
                        Long expectedBestResult = ResultsAggregator.calculateBestResult(results);
                        if (!expectedBestResult.equals(bestResult)) {
                            String formattedExpectedBest = CellFormatter.formatTime(expectedBestResult, resultFormat);
                            validationErrors.add(new ValidationError(Severity.HIGH, "Best result does not match calculated best result: " + formattedExpectedBest,
                                    aMatchedSheet, sheetRow, bestCellCol));
                            allRowsValid = false;
                        }
                    }
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError(Severity.HIGH, "Best result: " + e.getMessage(), aMatchedSheet, sheetRow, bestCellCol));
                    allRowsValid = false;
                }
            }
            else {
                bestResults[rowIdx] = results[0];
            }

            // Validate single record.
            int singleRecordCellCol = RowTokenizer.getSingleRecordCell(format, event, columnOrder);
            Cell singleRecordCell = row.getCell(singleRecordCellCol);
            try {
                CellParser.parseRecord(singleRecordCell);
            }
            catch (ParseException e) {
                validationErrors.add(new ValidationError(Severity.HIGH, "Misformatted single record", aMatchedSheet, sheetRow, singleRecordCellCol));
            }

            // Validate average result.
            if (format == Format.MEAN_OF_3 || format == Format.AVERAGE_OF_5 ||
                    (format == Format.BEST_OF_3 && columnOrder == ColumnOrder.BLD_WITH_MEAN) ) {
                int averageCellCol = RowTokenizer.getAverageCell(format, event);
                Cell averageResultCell = row.getCell(averageCellCol);

                try {
                    Long averageResult;
                    if (round.isCombined()) {
                        averageResult = CellParser.parseOptionalAverageTime(averageResultCell, resultFormat, event, aFormulaEvaluator);
                    }
                    else {
                        averageResult = CellParser.parseMandatoryAverageTime(averageResultCell, resultFormat, event, aFormulaEvaluator);
                    }
                    averageResults[rowIdx] = averageResult;

                    if (allResultsInRowValid) {
                        if (allResultsInRowPresent) {
                            Long expectedAverageResult = ResultsAggregator.calculateAverageResult(results, format, event);
                            if (!expectedAverageResult.equals(averageResult)) {
                                String formattedExpectedAverage = CellFormatter.formatTime(expectedAverageResult, resultFormat);
                                validationErrors.add(new ValidationError(Severity.HIGH, "Average result does not match calculated average result: " + formattedExpectedAverage,
                                        aMatchedSheet, sheetRow, averageCellCol));
                                allRowsValid = false;
                            }
                        }
                        else {
                            if (!averageResult.equals(0L)) {
                                validationErrors.add(new ValidationError(Severity.HIGH, "Average result should be empty for incomplete average in combined round",
                                        aMatchedSheet, sheetRow, averageCellCol));
                                allRowsValid = false;
                            }
                        }
                    }
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError(Severity.HIGH, "Average result: " + e.getMessage(), aMatchedSheet, sheetRow, averageCellCol));
                    allRowsValid = false;
                }

                // Validate average record.
                int averageRecordCellCol = RowTokenizer.getAverageRecordCell(format, event);
                Cell averageRecordCell = row.getCell(averageRecordCellCol);
                try {
                    CellParser.parseRecord(averageRecordCell);
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError(Severity.HIGH, "Misformatted average record", aMatchedSheet, sheetRow, averageRecordCellCol));
                }
            }
        }

        if(allCellsPresent) {
        	// If all time cells were filled in, the format of the round should *not* be combined.
        	if(aMatchedSheet.getRound().isCombined()) {
        		validationErrors.add(new ValidationError(Severity.HIGH,
                        "Sheet with all cells filled in should not be a combined round",
                        aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX));
        	}
        }

        // Check sorting.
        if (allRowsValid) {
            List<ResultRow> resultRows = new ArrayList<ResultRow>();
            for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
                int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();
                ResultRow resultRow = new ResultRow(sheetRow, bestResults[rowIdx], averageResults[rowIdx]);
                resultRows.add(resultRow);
            }

            Comparator<ResultRow> resultRowComparator;
            if (format == Format.MEAN_OF_3 || format == Format.AVERAGE_OF_5) {
                resultRowComparator = new AverageResultRowComparator();
            }
            else {
                resultRowComparator = new BestResultRowComparator();
            }
            Collections.sort(resultRows, resultRowComparator);

            boolean isSorted = true;
            for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
                int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();
                ResultRow sortedResultRow = resultRows.get(rowIdx);
                int rowDifference = sortedResultRow.getRowIdx() - sheetRow;
                if (rowDifference != 0) {
                    String direction = rowDifference > 0 ? "higher" : "lower";
                    int absRowDiff = Math.abs(rowDifference);
                    String number = absRowDiff > 1 ? "rows" : "row";
                    validationErrors.add(new ValidationError(Severity.LOW, "Bad sorting: row should be " + absRowDiff + " " + number + " " + direction,
                            aMatchedSheet, sortedResultRow.getRowIdx(), -1));
                    isSorted = false;
                }
            }

            // Check positions.
            if (isSorted) {
                Long expectedPosition = 1L;
                for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
                    if (rowIdx > 0) {
                        ResultRow lastRow = resultRows.get(rowIdx - 1);
                        ResultRow currentRow = resultRows.get(rowIdx);
                        if (resultRowComparator.compare(lastRow, currentRow) != 0) {
                            expectedPosition = rowIdx + 1L;
                        }
                    }
                    else {
                        expectedPosition = 1L;
                    }
                    int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();
                    Row row = sheet.getRow(sheetRow);
                    Long position = CellParser.parsePosition(row.getCell(0));
                    if (position != null && !expectedPosition.equals(position)) {
                        validationErrors.add(new ValidationError(Severity.LOW, "Position does not match expected position: " + expectedPosition,
                                aMatchedSheet, sheetRow, 0));
                    }
                }
            }
        }

        // Sort validation errors by row and cell.
        Collections.sort(validationErrors, new ValidationErrorComparator());
    }

    private static boolean validateWCAId(String aWcaId) {
        return aWcaId.matches("[0-9]{4}[A-Z]{4}[0-9]{2}");
    }

    private static class AverageResultRowComparator implements Comparator<ResultRow> {

        private ResultComparator fResultComparator = new ResultComparator();

        @Override
        public int compare(ResultRow aFirstResultRow, ResultRow aSecondResultRow) {
            int bestCompare = fResultComparator.compare(aFirstResultRow.getBestResult(),
                    aSecondResultRow.getBestResult());
            if (aFirstResultRow.getAverageResult() != 0L && aSecondResultRow.getAverageResult() != 0L) {
                int averageCompare = fResultComparator.compare(aFirstResultRow.getAverageResult(),
                        aSecondResultRow.getAverageResult());
                if (averageCompare != 0) {
                    return averageCompare;
                }
                else {
                    return bestCompare;
                }
            }
            else if (aFirstResultRow.getAverageResult() != 0L && aSecondResultRow.getAverageResult() == 0L) {
                return -1;
            }
            else {
                return bestCompare;
            }
        }

    }

    private static class BestResultRowComparator implements Comparator<ResultRow> {

        private ResultComparator fResultComparator = new ResultComparator();

        @Override
        public int compare(ResultRow aFirstResultRow, ResultRow aSecondResultRow) {
            return fResultComparator.compare(aFirstResultRow.getBestResult(), aSecondResultRow.getBestResult());
        }

    }

    private static class ValidationErrorComparator implements Comparator<ValidationError> {

        @Override
        public int compare(ValidationError aFirstValidationError, ValidationError aSecondValidationError) {
            if (aFirstValidationError.getRowIdx() == -1) {
                return -1;
            }
            else {
                if (aSecondValidationError.getRowIdx() == -1) {
                    return 1;
                }
                else {
                    if (aFirstValidationError.getCellIdx() == -1 && aSecondValidationError.getCellIdx() != -1) {
                        return -1;
                    }
                    else if (aFirstValidationError.getCellIdx() != -1 && aSecondValidationError.getCellIdx() == -1) {
                        return 1;
                    }
                    else if (aFirstValidationError.getCellIdx() == -1 && aSecondValidationError.getCellIdx() == -1) {
                        return aFirstValidationError.getRowIdx() - aSecondValidationError.getRowIdx();
                    }
                    else {
                        int rowDifference = aFirstValidationError.getRowIdx() - aSecondValidationError.getRowIdx();
                        if (rowDifference == 0) {
                            return aFirstValidationError.getCellIdx() - aSecondValidationError.getCellIdx();
                        }
                        else {
                            return rowDifference;
                        }
                    }
                }
            }
        }

    }

    private static class DecreasingCompetitorCountComparator implements Comparator<MatchedSheet> {

        @Override
        public int compare(MatchedSheet aFirstMatchedSheet, MatchedSheet aSecondMatchedSheet) {
            return (aSecondMatchedSheet.getLastDataRow() - aSecondMatchedSheet.getFirstDataRow()) -
                    (aFirstMatchedSheet.getLastDataRow() - aFirstMatchedSheet.getFirstDataRow());
        }

    }

}
