package model;

import java.util.Set;

public class DataScience extends CareerPath {
    @Override public String getName() { return "Data Science"; }
    @Override public String getDescription() {
        return "Extracting insight from data using statistics, ML and visualization.";
    }
    @Override public Set<String> getRequiredSkills() {
        return skills("python", "statistics", "machine learning", "sql", "pandas", "data visualization", "linear algebra");
    }
    @Override public String[] getTypicalRoles() {
        return new String[]{"Data Analyst", "Data Scientist", "ML Engineer", "Business Analyst"};
    }
}
