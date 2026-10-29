package vn.dienmay.server;

import java.util.Objects;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.service.AuthService;
import vn.dienmay.server.service.CatalogService;
import vn.dienmay.server.service.InventoryService;
import vn.dienmay.server.service.PeopleService;
import vn.dienmay.server.service.PromotionService;
import vn.dienmay.server.service.ReportService;
import vn.dienmay.server.service.SalesService;
import vn.dienmay.server.service.WarrantyService;

/** Backend composition root. No singleton, mutable global session or public dependency fields. */
public final class InventorySystem {
  private final SqlDatabase database;
  private final AuthService auth;
  private final CatalogService catalog;
  private final SalesService sales;
  private final InventoryService inventory;
  private final PeopleService people;
  private final PromotionService promotions;
  private final WarrantyService warranties;
  private final ReportService reports;

  public InventorySystem(SqlDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
    auth = new AuthService(database);
    catalog = new CatalogService(database, auth);
    sales = new SalesService(database, auth);
    inventory = new InventoryService(database, auth);
    people = new PeopleService(database, auth);
    promotions = new PromotionService(database, auth);
    warranties = new WarrantyService(database, auth);
    reports = new ReportService(database, auth);
  }

  public SqlDatabase database() {
    return database;
  }

  public AuthService auth() {
    return auth;
  }

  public CatalogService catalog() {
    return catalog;
  }

  public SalesService sales() {
    return sales;
  }

  public InventoryService inventory() {
    return inventory;
  }

  public PeopleService people() {
    return people;
  }

  public PromotionService promotions() {
    return promotions;
  }

  public WarrantyService warranties() {
    return warranties;
  }

  public ReportService reports() {
    return reports;
  }
}
