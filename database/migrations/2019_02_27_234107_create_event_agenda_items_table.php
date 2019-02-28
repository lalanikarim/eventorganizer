<?php

use Illuminate\Support\Facades\Schema;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Database\Migrations\Migration;

class CreateEventAgendaItemsTable extends Migration
{
    /**
     * Run the migrations.
     *
     * @return void
     */
    public function up()
    {
        Schema::create('event_agenda_items', function (Blueprint $table) {
            $table->unsignedBigInteger('id');
            $table->unsignedBigInteger('eventId');
            $table->unsignedBigInteger('agendaTypeId');
            $table->text('prenotes');
            $table->timestamps();

            $table->primary(['id','eventId']);
            $table->unsignedBigInteger('id')->autoIncrement()->change();

            $table->foreign('eventId')->references('id')->on('events');
            $table->foreign('agendaTypeId')->references('id')->on('agenda_types');
        });

    }

    /**
     * Reverse the migrations.
     *
     * @return void
     */
    public function down()
    {
        Schema::dropIfExists('event_agenda_items');
    }
}
