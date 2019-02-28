@extends('layouts.app')
@section('content')
  <div class="container">
    <div class="row justify-content-left">
      <div class="col-md-12">
        <div class="card">
          <div class="card-body">
            <h4 class="card-title">Events</h4>
            @include('events.list',['events' => $events])
            @include('events.create')
          </div>
        </div>
      </div>
    </div>
  </div>
@endsection