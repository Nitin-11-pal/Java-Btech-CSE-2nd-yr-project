package com.realestate.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Generic Data Access Object (DAO) interface defining standard CRUD operations.
 *
 * @param <T> the domain model type managed by this DAO
 */
// [RUBRIC: Collections] [RUBRIC: JDBC] Generic DAO<T> interface
public interface DAO<T> {

    /**
     * Inserts a new entity record into the database and populates its generated ID.
     *
     * @param entity the entity to persist
     * @return the persisted entity with generated primary key
     * @throws SQLException if a database error occurs
     */
    T insert(T entity) throws SQLException;

    /**
     * Updates an existing entity record in the database.
     *
     * @param entity the entity with updated values
     * @return true if at least one row was updated
     * @throws SQLException if a database error occurs
     */
    boolean update(T entity) throws SQLException;

    /**
     * Deletes an entity record by its primary key ID.
     *
     * @param id primary key of the record to delete
     * @return true if a row was deleted
     * @throws SQLException if a database error occurs
     */
    boolean delete(int id) throws SQLException;

    /**
     * Finds a single entity record by its primary key ID.
     *
     * @param id primary key to look up
     * @return Optional containing the entity if found, or empty Optional
     * @throws SQLException if a database error occurs
     */
    Optional<T> findById(int id) throws SQLException;

    /**
     * Retrieves all records for this entity type.
     *
     * @return List of all entities
     * @throws SQLException if a database error occurs
     */
    List<T> findAll() throws SQLException;
}
