<?php

use Illuminate\Support\Facades\Schema;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Database\Migrations\Migration;

class CreateEventTypeAgendasTable extends Migration
{
    /**
     * Run the migrations.
     *
     * @return void
     */
    public function up()
    {
        Schema::create('event_type_agendas', function (Blueprint $table) {
            $table->unsignedInteger('id');
            $table->unsignedBigInteger('eventTypeId');
            $table->unsignedBigInteger('agendaTypeId');
            $table->timestamps();

            $table->primary(['id','eventTypeId','agendaTypeId']);

            $table->foreign('eventTypeId')->references('id')->on('event_types');
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
        Schema::dropIfExists('event_type_agendas');
    }
}
