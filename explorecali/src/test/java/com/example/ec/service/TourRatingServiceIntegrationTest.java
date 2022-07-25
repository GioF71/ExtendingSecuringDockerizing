package com.example.ec.service;

import com.example.ec.domain.Difficulty;
import com.example.ec.domain.Region;
import com.example.ec.domain.Tour;
import com.example.ec.domain.TourPackage;
import com.example.ec.domain.TourRating;
import com.example.ec.repo.TourPackageRepository;
import com.example.ec.repo.TourRepository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by Mary Ellen Bowman
 */
@RunWith(SpringRunner.class)
@TestPropertySource(properties = { "spring.jpa.hibernate.ddl-auto=create" })
@SpringBootTest
@Transactional
public class TourRatingServiceIntegrationTest {
    private static final int CUSTOMER_ID = 456;
    private static final int TOUR_ID = 1;
    private static final int NOT_A_TOUR_ID = 123;

    @Autowired
    private TourRatingService ratingService;

    @Autowired
    private TourPackageRepository tourPackageRepository;

    @Autowired
    private TourRepository tourRepository;

    // Happy Path delete existing TourRating.
    private Tour createRandomTour(TourPackage tourPackage) {
        Tour newTour = new Tour("tour", "description", "blurb", 1000, "1000", "4",
                "keyword", tourPackage, Difficulty.Easy, Region.Central_Coast);
        tourRepository.save(newTour);
        return newTour;
    }

    private TourPackage createRandomTourPackage() {
        TourPackage tourPackage = new TourPackage(UUID.randomUUID().toString(), "my_tour_package");
        tourPackageRepository.save(tourPackage);
        return tourPackage;
    }

    @Test
    public void delete() {
        TourPackage tourPackage = createRandomTourPackage();
        Tour newTour = createRandomTour(tourPackage);

        ratingService.createNew(newTour.getId(), CUSTOMER_ID, 2, "it was fair");

        // a tour rating should exist
        List<TourRating> tourRatings = ratingService.lookupAll();
        ratingService.delete(tourRatings.get(0).getTour().getId(), tourRatings.get(0).getCustomerId());
        assertThat(ratingService.lookupAll().size(), is(tourRatings.size() - 1));
    }

    // UnHappy Path, Tour NOT_A_TOUR_ID does not exist
    @Test(expected = NoSuchElementException.class)
    public void deleteException() {
        ratingService.delete(NOT_A_TOUR_ID, 1234);
    }

    // Happy Path to Create a new Tour Rating
    @Test
    public void createNew() {
        // would throw NoSuchElementException if TourRating for TOUR_ID by CUSTOMER_ID
        // already exists
        TourPackage tourPackage = createRandomTourPackage();
        Tour newTour = createRandomTour(tourPackage);

        ratingService.createNew(newTour.getId(), CUSTOMER_ID, 2, "it was fair");

        // Verify New Tour Rating created.
        TourRating newTourRating = ratingService.verifyTourRating(newTour.getId(), CUSTOMER_ID);
        assertThat(newTourRating.getTour().getId(), is(newTour.getId()));
        assertThat(newTourRating.getCustomerId(), is(CUSTOMER_ID));
        assertThat(newTourRating.getScore(), is(2));
        assertThat(newTourRating.getComment(), is("it was fair"));
    }

    // UnHappy Path, Tour NOT_A_TOUR_ID does not exist
    @Test(expected = NoSuchElementException.class)
    public void createNewException() {
        ratingService.createNew(NOT_A_TOUR_ID, CUSTOMER_ID, 2, "it was fair");
    }

    // Happy Path many customers Rate one tour
    @Test
    public void rateMany() {
        TourPackage tourPackage = createRandomTourPackage();

        Tour newTour = createRandomTour(tourPackage);

        Integer[] initialRatingCustomers = { 98, 99 };
        Integer[] moreRatingCustomers = { 100, 101, 102 };
        // create 3 initial ratings
        for (int i = 0; i < initialRatingCustomers.length; ++i) {
            ratingService.createNew(newTour.getId(), initialRatingCustomers[i], 2, "it was fair");
        }

        int ratings = ratingService.lookupAll().size();
        assertThat(ratings, is(initialRatingCustomers.length));
        ratingService.rateMany(newTour.getId(), 5, moreRatingCustomers);
        assertThat(ratingService.lookupAll().size(), is(initialRatingCustomers.length + moreRatingCustomers.length));
    }

    // Unhappy Path, 2nd Invocation would create duplicates in the database,
    // DataIntegrityViolationException thrown
    @Test(expected = DataIntegrityViolationException.class)
    // @Test
    public void rateManyProveRollback() {
        TourPackage tourPackage = createRandomTourPackage();

        Tour newTour = createRandomTour(tourPackage);

        int ratings = ratingService.lookupAll().size();
        Integer customers[] = { 100, 101, 102 };

        ratingService.rateMany(newTour.getId(), 3, customers);
        System.out.println("Current Ratings (Count) " + ratingService.lookupAll().size());
        assertThat(ratings + customers.length, is(ratingService.lookupAll().size()));

        // this should break
        ratingService.rateMany(newTour.getId(), 3, customers);
    }

    // Happy Path, Update a Tour Rating already in the database
    @Test
    public void update() {
        TourPackage tourPackage = createRandomTourPackage();
        Tour newTour = createRandomTour(tourPackage);

        ratingService.createNew(newTour.getId(), CUSTOMER_ID, 3, "three");

        TourRating tourRating = ratingService.update(newTour.getId(), CUSTOMER_ID, 1, "one");
        assertThat(tourRating.getTour().getId(), is(newTour.getId()));
        assertThat(tourRating.getCustomerId(), is(CUSTOMER_ID));
        assertThat(tourRating.getScore(), is(1));
        assertThat(tourRating.getComment(), is("one"));
    }

    // Unhappy path, no Tour Rating exists for tourId=1 and customer=1
    @Test(expected = NoSuchElementException.class)
    public void updateException() throws Exception {
        ratingService.update(1, 1, 1, "one");
    }

    // Happy Path, Update a Tour Rating already in the database
    @Test
    public void updateSome() {
        TourPackage tourPackage = createRandomTourPackage();
        Tour newTour = createRandomTour(tourPackage);

        ratingService.createNew(newTour.getId(), CUSTOMER_ID, 3, "two");

        TourRating tourRating = ratingService.update(newTour.getId(), CUSTOMER_ID, 1, "one");
        assertThat(tourRating.getTour().getId(), is(newTour.getId()));
        assertThat(tourRating.getCustomerId(), is(CUSTOMER_ID));
        assertThat(tourRating.getScore(), is(1));
        assertThat(tourRating.getComment(), is("one"));
    }

    // Unhappy path, no Tour Rating exists for tourId=1 and customer=1
    @Test(expected = NoSuchElementException.class)
    public void updateSomeException() throws Exception {
        ratingService.update(1, 1, 1, "one");
    }

    // Happy Path get average score of a Tour.
    @Test
    public void getAverageScore() {
        TourPackage tourPackage = createRandomTourPackage();
        Tour newTour = createRandomTour(tourPackage);

        ratingService.createNew(newTour.getId(), 1, 3, "three");
        ratingService.createNew(newTour.getId(), 2, 4, "four");
        ratingService.createNew(newTour.getId(), 3, 5, "five");

        assertTrue(ratingService.getAverageScore(newTour.getId()) == 4.0);
    }

    // UnHappy Path, Tour NOT_A_TOUR_ID does not exist
    @Test(expected = NoSuchElementException.class)
    public void getAverageScoreException() {
        ratingService.getAverageScore(NOT_A_TOUR_ID); // That tour does not exist
    }
}