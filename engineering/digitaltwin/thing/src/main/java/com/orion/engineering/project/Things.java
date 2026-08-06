package com.orion.engineering.project;

import com.orion.engineering.project.model.ThingsDAO;
import com.orion.engineering.project.model.ThingModel;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Things
{
    @Autowired private ThingsDAO thingsDAO;


    public List<ThingModel> getThings()
    {
        return thingsDAO.findAll();
    }


    public long getNumberOfThings()
    {
        return thingsDAO.count();
    }
}
