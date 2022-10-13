package dk.medcom.healthcheck.dao;

import dk.medcom.healthcheck.dao.entity.HelloEntity;

import java.util.List;

public interface HelloDao {
    void insert(HelloEntity helloEntity);

    List<HelloEntity> findAll();
}