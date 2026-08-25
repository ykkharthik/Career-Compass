package test;

import exception.InvalidProfileException;
import model.SoftwareEngineering;
import model.Student;
import service.SkillGapService;

import java.util.LinkedHashSet;
import java.util.Set;

public class SkillGapServiceTest {

    private final SkillGapService skillGap = new SkillGapService();
    private final SoftwareEngineering softwareEngineering = new SoftwareEngineering();

    public void testMatchedAndMissingSkillsPartitionRequiredSkills() throws InvalidProfileException {
        Set<String> studentSkills = new LinkedHashSet<>(Set.of("java", "git", "sql", "photoshop"));
        Student student = new Student("test@example.com", "Test", 8.0, studentSkills, 5, 3, 2, 2, 2);

        Set<String> matched = skillGap.matchedSkills(student, softwareEngineering);
        Set<String> missing = skillGap.missingSkills(student, softwareEngineering);

        Assert.equal("matched skills", Set.of("java", "git", "sql"), matched);
        Assert.isFalse("a skill the student has shouldn't show up as missing", missing.contains("java"));
        Assert.isFalse("'photoshop' isn't required, so it must not appear in matched", matched.contains("photoshop"));

        Set<String> union = new LinkedHashSet<>(matched);
        union.addAll(missing);
        Assert.equal("matched + missing should equal every required skill", softwareEngineering.getRequiredSkills(), union);
    }

    public void testFullyQualifiedStudentHasNoGap() throws InvalidProfileException {
        Student student = new Student("test@example.com", "Test", 9.0,
                new LinkedHashSet<>(softwareEngineering.getRequiredSkills()), 5, 3, 2, 2, 2);
        Assert.isTrue("a student with every required skill has no gap",
                skillGap.missingSkills(student, softwareEngineering).isEmpty());
    }
}
