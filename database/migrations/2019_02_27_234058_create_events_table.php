<?php

use Illuminate\Support\Facades\Schema;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Database\Migrations\Migration;

class CreateEventsTable extends Migration
{
    /**
     * Run the migrations.
     *
     * @return void
     */
    public function up()
    {
        Schema::create('events', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('eventTypeId');
            $table->date('date');
            $table->unsignedBigInteger('locationId');
            $table->string('name');
            $table->integer('state')->default(0);
            $table->timestamps();

            $table->foreign('eventTypeId')->references('id')->on('event_types');
            $table->foreign('locationId')->references('id')->on('locations');
        });

    }

    /**
     * Reverse the migrations.
     *
     * @return void
     */
    public function down()
    {
        Schema::dropIfExists('events');
    }
}
