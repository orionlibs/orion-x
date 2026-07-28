package com.orion.engineering.project;

import com.orion.engineering.project.model.ProjectDAO;
import com.orion.engineering.project.model.ProjectModel;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Projects
{
    @Autowired private ProjectDAO projectDAO;


    public List<ProjectModel> getProjects()
    {
        return projectDAO.findAll();
    }
}
