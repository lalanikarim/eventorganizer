# --- !Ups
create table "LOCATIONS" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(25) NOT NULL);
create table "EVENTTYPES" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(25) NOT NULL);
create table "EVENTS" ("int" SERIAL NOT NULL PRIMARY KEY,"eventTypeId" INTEGER NOT NULL,"date" DATE NOT NULL,"locationId" INTEGER NOT NULL,"name" VARCHAR(50) NOT NULL);
create unique index "idx" on "EVENTS" ("eventTypeId","date","locationId");
create table "AGENDATYPES" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(25) NOT NULL);
create table "EVENTTYPEAGENDA" ("id" INTEGER NOT NULL,"eventTypeId" INTEGER NOT NULL,"agendaTypeId" INTEGER NOT NULL);
alter table "EVENTTYPEAGENDA" add constraint "pk_agendaItem" primary key("id","eventTypeId","agendaTypeId");
create table "EVENTAGENDAITEMS" ("id" SERIAL NOT NULL PRIMARY KEY,"eventId" INTEGER NOT NULL,"agendaTypeId" INTEGER NOT NULL,"notes" VARCHAR NOT NULL);
alter table "EVENTS" add constraint "event_fk_eventTypeId" foreign key("eventTypeId") references "EVENTTYPES"("id") on update CASCADE on delete RESTRICT;
alter table "EVENTS" add constraint "event_fk_locationId" foreign key("locationId") references "LOCATIONS"("id") on update CASCADE on delete RESTRICT;
alter table "EVENTTYPEAGENDA" add constraint "fk_agendaTypes" foreign key("agendaTypeId") references "AGENDATYPES"("id") on update CASCADE on delete RESTRICT;
alter table "EVENTTYPEAGENDA" add constraint "fk_eventTypes" foreign key("eventTypeId") references "EVENTTYPES"("id") on update CASCADE on delete RESTRICT;
alter table "EVENTAGENDAITEMS" add constraint "fk_agendaTypes" foreign key("agendaTypeId") references "AGENDATYPES"("id") on update CASCADE on delete RESTRICT;
alter table "EVENTAGENDAITEMS" add constraint "fk_events" foreign key("eventId") references "EVENTS"("int") on update CASCADE on delete RESTRICT;

# --- !Downs
alter table "EVENTAGENDAITEMS" drop constraint "fk_agendaTypes";
alter table "EVENTAGENDAITEMS" drop constraint "fk_events";
alter table "EVENTTYPEAGENDA" drop constraint "fk_agendaTypes";
alter table "EVENTTYPEAGENDA" drop constraint "fk_eventTypes";
alter table "EVENTS" drop constraint "event_fk_eventTypeId";
alter table "EVENTS" drop constraint "event_fk_locationId";
drop table "EVENTAGENDAITEMS";
alter table "EVENTTYPEAGENDA" drop constraint "pk_agendaItem";
drop table "EVENTTYPEAGENDA";
drop table "AGENDATYPES";
drop table "EVENTS";
drop table "EVENTTYPES";
drop table "LOCATIONS";