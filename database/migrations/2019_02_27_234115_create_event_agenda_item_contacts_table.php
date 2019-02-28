<?php

use Illuminate\Support\Facades\Schema;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Database\Migrations\Migration;

class CreateEventAgendaItemContactsTable extends Migration
{
    /**
     * Run the migrations.
     *
     * @return void
     */
    public function up()
    {
        Schema::create('event_agenda_item_contacts', function (Blueprint $table) {
            $table->unsignedBigInteger('id');
            $table->unsignedBigInteger('eventId');
            $table->unsignedBigInteger('contactId');
            $table->unsignedBigInteger('userId');
            $table->text('postnotes');
            $table->timestamps();

            $table->primary(['id','eventId','contactId']);
            $table->unsignedBigInteger('id')->autoIncrement()->change();

            $table->foreign('userId')->references('id')->on('users');
            $table->foreign(['id','eventId'])->references(['id','eventId'])->on('event_agenda_items');
            $table->foreign('contactId')->references('id')->on('contacts');
        });

    }

    /**
     * Reverse the migrations.
     *
     * @return void
     */
    public function down()
    {
        Schema::dropIfExists('event_agenda_item_contacts');
    }
}
