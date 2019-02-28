<?php

use Illuminate\Support\Facades\Schema;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Database\Migrations\Migration;

class CreateContactPreferencesTable extends Migration
{
    /**
     * Run the migrations.
     *
     * @return void
     */
    public function up()
    {
        Schema::create('contact_preferences', function (Blueprint $table) {
            $table->unsignedBigInteger('contactId');
            $table->unsignedBigInteger('agendaTypeId');
            $table->boolean('prefer');
            $table->timestamps();

            $table->primary(['contactId','agendaTypeId']);

            $table->foreign('contactId')->references('id')->on('contacts');
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
        Schema::dropIfExists('contact_preferences');
    }
}
